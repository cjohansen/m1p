# m1p: Map interpolation and DIY i18n++

m1p (short for "map" in the tradition of i18n) is a map interpolation library
than can be used for i18n (or just externalizing textual content for a single
language), theming, and similar use cases. Some assembly required. Batteries not
included. Bring your own bling.

## Install

With tools.deps:

```clj
no.cjohansen/m1p {:mvn/version "2022.11.15"}
```

With Leiningen:

```clj
[no.cjohansen/m1p "2022.11.15"]
```

## m1p's core value proposition

m1p makes it possible to loosely couple data processing code from textual
content by placing it in a dictionary, and to loosely couple its content from
app data that will eventually be interpolated into it.

1. You produce data with placeholders for text, theming, and other "flavor
   content".
2. m1p looks up content templates from dictionaries and inflates them with app
   data.
3. m1p folds inflated content templates into your data.
4. Et voila!

m1p was created with these goals:

- i18n, theming, and similar concerns should be handled orthogonal to data
  processing.
- Dictionaries should be serializable data.
- Dictionaries should support declarative interpolation and custom
  transformations.

## i18n with m1p

The best way to understand m1p (pronounced "meep", or "meep, meep" if you're
really feeling it) is through an example. In many ways, m1p is an i18n library
that isn't really an i18n library. It's very good at retrieving and
"just-in-time processing" values, but it knows nothing about locales,
pluralization, number formatting, and other i18n concerns. It can, however,
learn those things. We'll explore how m1p works by using it as a i18n library.

### Base case

m1p works with [dictionaries](#dictionary) built from plain serializable maps:

<a id="ex1"></a>
```clj
(require '[m1p.core :as m1p])

(def dictionary
  {:header/title "Hello, world!"})

(m1p/interpolate
 [:div.main
  [:h1 [:i18n :header/title]]]       ;; 1
 {:dictionaries {:i18n dictionary}}) ;; 2

;;=> [:div.main [:h1 "Hello, world!"]]
```

1. `[:i18n :header/title]` is a [reference tuple](#reference-tuple) that refers
   to the key `:header/title` in the `:i18n` dictionary.
2. m1p can interpolate from multiple dictionaries at once. This example only has
   one dictionary, the `:i18n` one.


### Where it gets interesting

Greeting the world is all well and good, but what if we desire a more personal
greeting? Well, then we have to fold some data into the template before folding
the result back into our data:

<a id="ex2"></a>
```clj
(m1p/interpolate
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]]
 {:dictionaries {:i18n dictionary}})
```

To achieve this we expanded the reference tuple to pass some data: `[:i18n
:header/title {:greetee "Internet"}]`.

We'll also update the dictionary to include `:greetee` with `[:fn/str ...]`
(more about this shortly):

<a id="ex2-1"></a>
```clj
(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:fn/str "Hello, {{:greetee}}!"]}))
```

All in all, it looks like this:

<a id="ex2-2"></a>
```clj
(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:fn/str "Hello, {{:greetee}}!"]}))

(m1p/interpolate
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]]
 {:dictionaries {:i18n dictionary}})

;;=> [:div.main [:h1 "Hello, Internet!"]]
```

Yay!

`m1p.core/prepare-dictionary` helps dictionaries do interesting things to their
values on retrieval: interpolate data, "pluralize" texts, format dates, apply
gradients to colors - your imagination is the limit. These transformations are
performed with [dictionary functions](#dictionary-functions).

`[:fn/str "Hello, {{:greetee}}"]` references the built-in dictionary function
[`:fn/str`](#fn-str), which performs classic mustachioed string interpolation.
It is possible and encoured to register your own [custom dictionary
functions](#custom-dictionary-functions).

Another one of m1p's built-in dictionary functions, [`:fn/get`](#fn-get), is
even simpler. It just gets parameters:

<a id="ex3"></a>
```clj
(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:span "Hello, "
                   [:strong [:fn/get :greetee]]]}))

(m1p/interpolate
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]]
 {:dictionaries {:i18n dictionary}})

;;=> [:div.main [:h1 [:span "Hello, " [:strong "Internet"]]]]
```

### Building dictionaries

`m1p.core/prepare-dictionary` enables the use of dictionary functions, and front
loads some processing for faster retrieval.

<a id="ex4"></a>
```clj
(require '[m1p.core :as m1p])

(def en-dictionary
  (m1p/prepare-dictionary
   [#:home
    {:title "Home page"                           ;; 1
     :text [:fn/str "Welcome {{:display-name}}"]} ;; 2

    #:login
    {:title "Log in"
     :help-text [:span {}                         ;; 3
                 "Need help? "
                 [:a {:href [:fn/get :url]}       ;; 4
                  "Go here"]]}]))
```

1. A dictionary can be specified as a vector of maps, in which case m1p will
   `(apply merge ,,,)` them. This makes it easy to use Clojure's namespace maps
   for a pleasant dictionary editing experience, and is very useful when
   combining dictionaries for several namespaces.
2. The `:fn/str` is a built-in dictionary function. It can be overridden.
3. Dictionary keys can contain any value.
4. Dictionary functions can be used anywhere in data structures.

<a id="custom-dictionary-functions"></a>
### Custom dictionary functions

Dictionary functions can bring any number of new features to m1p dictionaries.
These functions are called with:

- `opt` the map of options passed to `m1p.core/interpolate`.
- `params` - the data passed to the reference tuple looking up the dictionary
  key.
- Remaining values from the dictionary tuple referencing the dictionary
  function.

We will now consider two examples that are commonly used in i18n tooling.

### Date formatters

Dates are typically formatted differently under different locales, and here's
how to do it with dictionary functions:

<a id="ex6"></a>
```clj
(import '[java.time LocalDateTime]
        '[java.time.format DateTimeFormatter]
        '[java.util Locale])

(defn format-date [opt params pattern local-date]      ;; 1
  (when local-date
    (-> (DateTimeFormatter/ofPattern pattern)
        (.withLocale (Locale. (:locale opt)))          ;; 2
        (.format local-date))))

(def dictionary
  (m1p/prepare-dictionary
   {:updated-at [:fn/str "Last updated "                ;; 3
                 [:fn/date "E MMM d" [:fn/get :date]]]} ;; 4
   {:dictionary-fns {:fn/date format-date}}))           ;; 5

(m1p/interpolate
 {:text [:i18n :updated-at
         {:date (LocalDateTime/of 2022 6 8 9 37 12)}]}
 {:locale "en"                                          ;; 6
  :dictionaries {:i18n dictionary}})

;;=> {:text "Last updated Wed Jun 8"}
```

1. `format-date` uses Java libraries to format a local date. For ClojureScript
   there is `goog.i18n.DateTimeFormat`, which works in a very similar way.
2. `opt` is passed from the call to `m1p.core/interpolate` and is a convenient
   way to pass in things like the current locale.
3. Dictionary functions can be arbitrarily nested
4. The `:updated-at` key uses `:fn/get` to pass the date to the formatter.
5. "Install" the date formatter dictionary function.
6. Options passed to `interpolate` are available in dictionary functions.

Because the date argument is extracted from `params` with `:fn/get`, it must be
named in the reference tuple. When dictionary keys only refer to a single value,
you can use the dictionary function `:fn/param` to select the single argument
instead:

<a id="ex8"></a>
```clj
(def dictionary
  (m1p/prepare-dictionary
   {:updated-at [:fn/str "Last updated "
                 [:fn/date "E MMM d" [:fn/param]]]
    :created-at [:fn/str "Created by {{:creator}} "
                 [:fn/date "E MMM d" [:fn/get :date]]]}
   {:dictionary-fns {:fn/date format-date}}))

(m1p/interpolate
 {:created [:i18n :created-at {:creator "Christian"
                               :date (LocalDateTime/of 2022 6 8 8 37 12)}]
  :updated [:i18n :updated-at (LocalDateTime/of 2022 6 8 9 37 12)]}
 {:locale "en"
  :dictionaries {:i18n dictionary}})

;;=>
;; {:created "Created by Christian Wed Jun 8"
;;  :updated "Last updated Wed Jun 8"}
```

#### Pluralization

Pluralization is a hard problem to solve properly across all languages, but
usually a trivial matter to implement for the handful of languages a specific
app supports.

Here's how to add a naive "0, 1, many"-style pluralization helper to a
dictionary:

<a id="ex5"></a>
```clj
(require '[m1p.core :as m1p])

(defn pluralize [opt n & plurals]
  (-> (nth plurals (min (if (number? n) n 0) (dec (count plurals))))
      (m1p/interpolate-string {:n n} opt)))

(def dictionary
  (m1p/prepare-dictionary
   {:songs [:fn/plural "no songs" "one song" "a couple of songs" "{{:n}} songs"]}
   {:dictionary-fns {:fn/plural pluralize}}))

(m1p/interpolate
 [:ul
  [:li [:i18n :songs 0]]
  [:li [:i18n :songs 1]]
  [:li [:i18n :songs 2]]
  [:li [:i18n :songs 4]]]
 {:dictionaries {:i18n dictionary}})

;;=>
;; [:ul
;;  [:li "no songs"]
;;  [:li "one song"]
;;  [:li "a couple of songs"]
;;  [:li "4 songs"]]
```

### But what about multiple languages?

Seems a little weird for an "i18n library" to not touch on how to switch between
different languages and locales, no? Turns out switching between dictionaries
isn't the hard part: just check the current locale and select the right
dictionary to pass to `interpolate`:

```clj
(def dictionary-opts
  {:dictionary-fns {:fn/date format-date
                    :fn/plural pluralize}})

(def dictionaries
  {:en (m1p/prepare-dictionary
        {:title [:fn/str "Hello {{:display-name}}!"]}
        dictionary-opts)

   :nb (m1p/prepare-dictionary
        {:title [:fn/str "Hei {{:display-name}}!"]}
        dictionary-opts)})

(def locale :nb)

(m1p/interpolate
 [:i18n :title {:display-name "Meep meep"}]
 {:dictionaries {:i18n (get dictionaries locale)}})

;;=> "Hei Meep meep!"
```

## Parity tests

Because dictionaries will be used interchangeably, it is a good idea to test
them for parity. The `m1p.validation` namespace contains several functions that
can detect common problems. You can combine these however you want, and possibly
add some of your own and perform assertions on them in your test suite, during
builds, or similar.

<a id="ex10"></a>
```clj
(require '[m1p.core :as m1p]
         '[m1p.validation :as v])

(def dicts
  {:en (m1p/prepare-dictionary
        [#:home
         {:title "Home page"
          :text [:fn/str "Welcome {{:display-name}}"]}

         #:login
         {:title "Log in"
          :help-text [:span {}
                      "Need help? "
                      [:a {:href [:fn/get :url]}
                       "Go here"]]}])

   :nb (m1p/prepare-dictionary
        [#:home
         {:title "Hjemmeside"
          :text "Welcome {{:display-name}}"} ;; Missing [:fn/str ,,,]

         #:login
         {:title "Logg inn"}])})

(concat
 (v/find-non-kw-keys dicts)
 (v/find-unqualified-keys dicts)
 (v/find-missing-keys dicts)
 (v/find-misplaced-interpolations dicts)
 (v/find-type-discrepancies dicts)
 (v/find-interpolation-discrepancies dicts)
 (v/find-fn-get-param-discrepancies dicts))

;;=>
;; [{:kind :missing-key
;;   :dictionary :nb
;;   :key :login/help-text}
;;  {:kind :misplaced-interpolation-syntax
;;   :dictionary :nb
;;   :key :home/text}
;;  {:kind :interpolation-discrepancy
;;   :key :home/text
;;   :dictionaries {:en #{:display-name}
;;                  :nb #{}}}]
```

All the validation functions return a list of potential problems in your
dictionaries. The data can be used to generate warnings and/or errors as you see
fit. For a more human consumable report, pass the data to
`m1p.validation/print-report` (which formats the data with
`m1p.validation/format-report` and prints it):

<a id="ex11"></a>
```clj
(->> (concat
      (v/find-non-kw-keys dicts)
      (v/find-unqualified-keys dicts)
      (v/find-missing-keys dicts)
      (v/find-misplaced-interpolations dicts)
      (v/find-type-discrepancies dicts)
      (v/find-interpolation-discrepancies dicts)
      (v/find-fn-get-param-discrepancies dicts))
     (v/print-report dicts))

;; Problems in :nb
;;   String interpolation syntax outside :fn/str:
;;     :home/text
;;
;;   Missing keys:
;;     :login/help-text
;;
;; Interpolation discrepancies
;;   :home/text
;;     :en #{:display-name}
;;     :nb #{}
```

## Reference

<a id="dictionary"></a>
### Dictionary

A dictionary is just a map. One of the core ideas in m1p is to create this map
in such a way that its source can be serializable Clojure data. For runtime use,
pass it through `m1p.core/prepare-dictionary` which turns some values into
functions.

Because serializable dictionaries is an important goal for m1p, the dictionary
can contain [reference tuples](#reference-tuple) that turn into function calls
on retrieval.

m1p does not have anything akin to `get-in`, so dictionaries is just one flat
key-value data structure. For this reason, it is highly suggested to use
namespaced keys in dictionaries.

<a id="reference-tuple"></a>
### Reference tuple

A reference tuple is a vector where the first element is a keyword that has
meaning to m1p:

```clj
[:some-keyword ,,,]
```

There are two main forms of reference tuples:

1. References to dictionary keys in data passed to `m1p.core/interpolate`
2. References to [dictionary functions](#dictionary-functions) in dictionary
   values.

#### Dictionary lookups

Dictionary key lookups look like this:

```clj
[dictionary-k k & [params]]
```

That is:

- `dictionary-k` - A key referencing a dictionary
- `k` - A key in said dictionary
- `params` - Optional argument to dictionary functions

Dictionary key lookups will be replaced with the result of:

```clj
(m1p.core/lookup opt dictionary k params)
```

Where does the `dictionary-k` key come from? When calling
`m1p.core/interpolate`, dictionaries are passed like so:

```clj
(m1p.core/interpolate params {:dictionaries {k dict}})
```

The `k` is the value to use in lookup references in `params` to lookup keys in
that dictionary.

#### Dictionary function references

References to dictionary functions look like this:

```clj
[fn-k & args]
```

Available `fn-k`s are determined when calling `m1p.core/prepare-dictionary`:

```clj
(m1p/prepare-dictionary
 dictionary-map

 {:dictionary-fns {k fn}})
```

Any `k` in the `:dictionary-fns` map can be used in reference tuples in the
dictionary to have the associated function called on retrieval, in addition to
built-in dictionary functions, see below.

<a id="dictionary-functions"></a>
### Dictionary functions

A dictionary function is a function that can be invoked on retrieval based on
declarative data in the dictionary. Dictionary functions are passed to
`m1p.core/prepare-dictionary` in the second argument under
`:dictionary-functions`:

```clj
(m1p/prepare-dictionary
 dictionary-map

 {:dictionary-fns {k fn}})
```

See [reference tuples](#reference-tuple) for more information about referencing
these in dictionaries.

Dictionary functions are called with `opts` from the call to
`m1p.core/interpolate`, `params` from the dictionary key lookup and any
remaining items from the reference tuple that invoked the function.

This reference lookup:

```clj
(def dictionary
  (m1p.core/prepare-dictionary
   {:songs [:fn/plural "no songs" "one song" "{{:n}} songs"]}
   {:dictionary-fns {:fn/plural pluralize}}))
```

And this interpolation:

```clj
(m1p.core/interpolate
  [:i18n :songs 0]
  {:dictionaries {:i18n dictionary}
   :fn.str/on-missing-interpolation (fn [_ _ k] (str "No" k "!"))})
```

Will result in `pluralize` being called with the options map passed to
`interpolate`, `0` as `params`, and then the strings `"no songs"`, `"one song"`,
and `"{{:n}} songs"`. In fewer words:

```clj
(def opt {:fn.str/on-missing-interpolation (fn [_ _ k] (str "No" k "!"))})

(pluralize opt 0 "no songs" "one song" "{{:n}} songs")
```

<a id="fn-str"></a>
### `[:fn/str & xs]`

A built-in [dictionary function](#dictionary-function). Performs string
interpolation on the strings `xs` and joins the strings with no separator. Any
occurrence of `{{:k}}` will be replaced with `:k` from `params`.

If a string contains an interpolation placeholder that is not provided in
`params` on retrieval, the behavior is defined by the function passed as
`:fn.str/on-missing-interpolation` in the options map to
`m1p.core/prepare-dictionary`. This function is called with an options map,
`params`, and the missing placeholder/key.

By default missing interpolations will render the string:

```clj
"[Missing interpolation key :k]"
```

You might want to provide a custom function for this to throw exceptions during
test and developent, and to log the problem and output an empty string in
production.

<a id="fn-get"></a>
### `[:fn/get k]`

Returns the key `k` in `params` in `[:i18n :dict/key params]`. Return the string
`"[Missing key k]"` if not found. Change this behavior by passing a function as
`:fn.get/on-missing-key` in the options map to `m1p.core/prepare-dictionary`.
The function will be called with an options map, `params` and the missing key.

You might want to provide a custom function for this to throw exceptions during
test and developent, and to log the problem in production.

<a id="fn-param"></a>
### `[:fn/param]`

Returns the entire `params` as passed to `interpolate` in place.

<a id="prepare-dictionary"></a>
### `(m1p.core/prepare-dictionary dictionary opt)`

Enables the use of dictionary functions in `dictionary`. `opt` is a map of
options:

- `:on-missing-dictionary-key` a function to call when attempting to interpolate
  a dictionary key that can't be found. Will be called with `opts` from
  `interpolate`, params, and the key.

- `:exception-handler` a function to call when a dictionary function throws an
  exception. The handler receives an ex-info with ex-data in this shape:

      ```clj
      {:fn, :lookup-key, :data}
      ```

  The handler function should return the value to interpolate in place of the
  failed call. Popular option: Logging the error and returning `nil`.

<a id="interpolate"></a>
### `(m1p.core/interpolate data opt)`

Interpolate `data` with keys from `:dictionaries` in `opt`. Additional options
in `opt` are passed to dictionary functions.

<a id="lookup"></a>
### `(m1p.core/lookup opt dictionary k & [params])`

Lookup a single key `k` from the `dictionary`. `opt` is passed to dictionary
functions.

## Dictionary validation

Functions that finds common problems in dictionaries that are to be used
interchangeably. In all these functions, `dicts` is a map of dictionaries to be
compared, e.g. `{:en dict, :nb dict}`. All functions return a list of problems
as maps with the keys `:dictionary` (e.g. `:en`), `:key`, `:kind` (the kind of
problem), and optionally `:data`. Feel free to create your own validation
functions that work on the same data structures.

<a id="find-non-kw-keys"></a>
### `(m1p.validation/find-non-kw-keys dicts)`

Return a list of all keys across all dictionaries that are not keywords.

<a id="find-unqualified-keys"></a>
### `(m1p.validation/find-unqualified-keys dicts)`

Return a list of all keys across all dictionaries that don't have a namespace.

<a id="find-missing-keys"></a>
### `(m1p.validation/find-missing-keys dicts)`

Finds all keys used across all dictionaries and returns a list of keys missing
from individual dictionaries.

<a id="find-type-discrepancies"></a>
### `(m1p.validation/find-type-discrepancies dicts)`

Returns a list of all keys whose type is not the same across all dictionaries.
The list will include one entry per dictionary for each key with type
discrepancies.

<a id="find-interpolation-discrepancies"></a>
### `(m1p.validation/find-interpolation-discrepancies dicts)`

Returns a list of all keys whose values use a different set of string
interpolations.

<a id="find-fn-get-param-discrepancies"></a>
### `(m1p.validation/find-fn-get-param-discrepancies dicts)`

Returns a list of all keys whose values use a different set of parameters with
`:fn/get`.

<a id="get-label"></a>
### `(m1p.validation/get-label dicts)`

A multi-method that returns a string label for the printed report for a `:kind`.
If you add your own validation functions, implement this to label your custom
problems, so `format-report` and `print-report` can treat your problems like
m1p's own:

```clj
(defmethod m1p.validation/get-label :my-custom-problem [_] "Custom problem")
```

<a id="format-report"></a>
### `(m1p.validation/format-report dicts problems)`

Formats problems in a human-readable string.

<a id="print-report"></a>
### `(m1p.validation/print-report dicts problems)`

Prints the report formatted by `m1p.validation/format-report`.

## License

Copyright © 2022 Christian Johansen & [Magnar Sveen](https://github.com/magnars)
Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
