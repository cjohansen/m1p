# m1p: Map interpolation and DIY i18n++

m1p (in the tradition of i18n and friends: short for "map") is a map
interpolation library than can be used for i18n, theming, and similar use cases.
Some assembly required. Batteries not included. Bring your own bling.

## m1p's core value proposition

m1p enables you to loosely couple your data processing code from details like
textual content, and to loosely couple said content from app data that will
eventually be interpolated into it.

m1p was created with these goals:

- Dictionaries should be serializable data
- Dictionaries should support declarative interpolation
- i18n, theming, and similar concerns should be handled orthogonal to data
  processing

## i18n with m1p

The best way to understand m1p (pronounced "meep", or "meep, meep" if you're
really feeling it) is through an example. In many ways, m1p is an i18n library
that isn't really an i18n library. It's very good at retrieving and
"just-in-time processing" values, but it knows nothing about locales,
pluralization, number formatting, and other i18n concerns. It can, however,
learn those things from you. We'll explore how m1p works by using it as a i18n
library.

m1p works with [dictionaries](#dictionary). It is built from a plain
serializable map. Passing the map through `m1p.core/prepare-dictionary`, helps
it do interesting things to its values on retrieval: interpolate data from your
program, "pluralize" texts, format dates, apply gradients to colors - your
imagination is the limit. These transformations are performed with [dictionary
functions](#dictionary-functions), and can be best illustrated with one of m1p's
built-in functions:

<a id="ex1"></a>
```clj
(require '[m1p.core :as m1p])

(def dictionary
  (m1p/prepare-dictionary
   {:greeting [:fn/str "Hello, {{:greetee}}"]}))
```

The data-structure `[:fn/str "Hello, {{:greetee}}"]` is a core concept in m1p.
It's called a [reference tuple](#reference-tuple), and in this case it
references the built-in dictionary function [`:fn/str`](#fn-str). You can also
register custom dictionary functions. When you `m1p.core/lookup` a key that
contains a reference tuple like this, the associated function will be called
with parameters passed to `m1p.core/lookup`:

<a id="ex2"></a>
```clj
(m1p/lookup dictionary :greeting {:greetee "World"})

;;=> "Hello, World"
```

Reference tuples can be placed anywhere. Here it's inside some hiccup data:

<a id="ex3"></a>
```clj
(def dictionary
  (m1p/prepare-dictionary
   {:text [:span "Hello, " [:bold [:fn/str "{{:greetee}}"]]]}))

(m1p/lookup dictionary :text {:greetee "World"})

;;=> [:span "Hello, " [:bold "World"]]
```

When the interpolation is isolated like above, you can use m1p's other built-in
dictionary function, [`:fn/get`](#fn-get), which just gets parameters:

<a id="ex4"></a>
```clj
(def dictionary
  (m1p/prepare-dictionary
   {:text [:span "Hello, " [:bold [:fn/get :greetee]]]}))

(m1p/lookup dictionary :text {:greetee "World"})

;;=> [:span "Hello, " [:bold "World"]]
```

### Interpolation

`m1p.core/lookup` is useful to look up the odd key, but using it every time you
need some textual content spreads i18n details all over your code. Consider this
login page data example:

<a id="ex5"></a>
```clj
(def dictionary
  (m1p/prepare-dictionary
   {:login/title [:fn/str "Log in to {{:site}}"]
    :login/text "Enter your email and we'll send you a code for login"
    :login/button-text "Gimme"
    :login/email-placeholder "Email"}))

(def page-data
  {:title (m1p/lookup dictionary :login/title {:site "My site"})
   :text (m1p/lookup dictionary :login/text)
   :form {:button {:disabled? true
                   :spinner? false
                   :text (m1p/lookup dictionary :login/button-text)}
          :inputs [{:on-input [[:assoc-in [:transient :email] :event/target.value]]
                    :placeholder (m1p/lookup dictionary :login/email-placeholder)}]}})
```

The page data preparation code is inextricably coupled to a concrete dictionary.
We can loosen the coupling with `m1p.core/interpolate` by replacing every call
to `m1p.core/lookup` with a [reference tuple](#reference-tuple) that names a
dictionary and a key where the desired template can be found.

<a id="ex6"></a>
```clj
(def page-data
  {:title [:i18n :login/title {:site "My site"}] ;; 1
   :text [:i18n :login/text]
   :form {:button {:disabled? true
                   :spinner? false
                   :text [:i18n :login/button-text]}
          :inputs [{:on-input [[:assoc-in [:transient :email] :event/target.value]]
                    :placeholder [:i18n :login/email-placeholder]}]}})

(m1p/interpolate
 {:dictionaries                                  ;; 2
  {:i18n                                         ;; 3
   (:en dictionaries)}}
 page-data)

;;=>
;; {:title "Log in to My site"
;;  :text "Enter your email and we'll send you a code for login"
;;  :form {:button {:disabled? true
;;                  :spinner? false
;;                  :text "Gimme"}
;;         :inputs [{:on-input [[:assoc-in [:transient :email] :event/target.value]]
;;                   :placeholder "Email"}]}}
```

1. Calls to `m1p.core/interpolate` have been replaced with a reference tuple.
   The `:i18n` keyword is not special: This tuple specifically says "perform
   `lookup` in the `:i18n` dictionary with the key `login/title`, and pass
   `{:site "My site"}` as params.
2. `m1p.core/interpolate` takes a map of `:dictionaries` because you can have
   several orthogonal dictionaries: one for i18n, one for theming, etc.
3. The key under which a dictionary lives in the `:dictionaries` map is the key
   you use in reference tuples to refer to keys from it.

With `interpolate`, textual content has been decoupled from the data processing
code. This enables you to write unit tests that don't fail on every minor copy
tweak, and choose to realize textual content or not for various uses of the
data. It also turns the dependency around such that the function that produced
`page-data` does not need to known about dictionaries, locales, themes or
related detials.

### Building dictionaries

`m1p.core/prepare-dictionary` enables the use of dictionary functions, and front
loads some processing for faster retrieval.

<a id="ex7"></a>
```clj
(require '[m1p.core :as m1p])

(def en-dictionary
  (m1p/prepare-dictionary
   [#:home
    {:title "Home page"                          ;; 1
     :text [:fn/str "Welcome {{display-name}}"]} ;; 2

    #:login
    {:title "Log in"
     :help-text [:span {}                        ;; 3
                 "Need help? "
                 [:a {:href [:fn/get :url]}      ;; 4
                  "Go here"]]}]))
```

1. A dictionary can be specified as a vector of maps, in which case m1p will
   `(apply merge ,,,)` them. This makes it easy to use Clojure's namespace maps
   for a pleasant dictionary editing experience, and is very useful when you
   combine dictionaries for several namespaces.
2. The `:fn/str` is a built-in dictionary function. It can be overridden.
3. Dictionary keys can contain any value.
4. You can use dictionary functions anywhere in data structures. `:fn/get` gives
   you interpolation that isn't limited to outputting strings.

### Custom dictionary functions

Dictionary functions can bring any number of new features to m1p dictionaries.
We'll consider two examples that are common used in i18n tooling.

#### Pluralization

Pluralization is a hard problem to solve properly across all languages, but
usually a trivial matter to implement for the handful of languages your app
supports.

Here's how you can add a naive "0, 1, many"-style pluralization helper to a
dictionary:

<a id="ex8"></a>
```clj
(require '[m1p.core :as m1p])

(defn pluralize [opt n & plurals]
  (-> (nth plurals (min (if (number? n) n 0) (dec (count plurals))))
      (m1p/interpolate-string {:n n} opt)))

(def dictionary
  (m1p/prepare-dictionary
   {:songs [:fn/plural "no songs" "one song" "{{:n}} songs"]}
   {:dictionary-fns {:fn/plural pluralize}}))

(m1p/lookup dictionary :songs 0) ;;=> "no songs"
(m1p/lookup dictionary :songs 1) ;;=> "one song"
(m1p/lookup dictionary :songs 4) ;;=> "4 songs"
```

If using `m1p.core/interpolate`, replace the calls to `lookup` with reference
tuples like so:

<a id="ex8-1"></a>
```clj
[:i18n :songs 0]
```

### Date formatters

Here's how you can use dictionary functions to format dates:

<a id="ex9"></a>
```clj
(import '[java.time LocalDateTime]
        '[java.time.format DateTimeFormatter]
        '[java.util Locale])

(defn format-date [opt data pattern local-date]          ;; 1
  (when local-date
    (let [locale (-> opt :dictionary :locale)]           ;; 2
      (-> (DateTimeFormatter/ofPattern pattern)
          (.withLocale (Locale. (name locale)))
          (.format local-date)))))

(def dictionary
  (m1p/prepare-dictionary
   {:locale :en                                          ;; 3
    :updated-at [:fn/date "YYYY-MM-dd" [:fn/get :date]]} ;; 4
   {:dictionary-fns {:fn/date format-date}}))            ;; 5

(m1p/interpolate
 {:dictionaries {:i18n dictionary}}
 {:text [:i18n :updated-at
         {:date (LocalDateTime/of 2022 6 8 9 37 12)}]})

;;=> {:text "2022-06-08"}
```

1. `format-date` uses Java libraries to format a local date. For ClojureScript
   you can use `goog.i18n.DateTimeFormat`, which works in a very similar way.
2. `opt` contains the current dictionary - stuffing the locale in it can be
   helpful when your dictionary functions do localizing things.
3. m1p doesn't known about locales, so we'll have to keep track of it ourselves.
4. The `:updated-at` key uses `:fn/get` to access the actual date to format.
5. "Install" the date formatter dictionary function.

## Reference

<a id="dictionary"></a>
### Dictionary

A dictionary is just a map. One of the core ideas in m1p is to create this map
in such a way that its source can be serializable Clojure data. For runtime use,
you pass it through `m1p.core/prepare-dictionary` which turns some values into
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
- `params` - Optional argument to `m1p.core/lookup`

Dictionary key lookups will be replaced with the result of:

```clj
(m1p.core/lookup dictionary k params)
```

Where does the `dictionary-k` key come from? When you call
`m1p.core/interpolate`, you pass dictionaries like so:

```clj
(m1p.core/interpolate {:dictionaries {k dict} data)
```

The `k` is the value to use in lookup references in `data` to lookup keys in
that dictionary.

#### Dictionary function references

References to dictionary functions look like this:

```clj
[fn-k & args]
```

Available `fn-k`s are determined when you call `m1p.core/prepare-dictionary`:

```clj
(m1p/prepare-dictionary
 dictionary-map

 {:dictionary-fns {k fn}})
```

Any `k` in the `:dictionary-fns` map can be used in reference tuples in the
dictionary to have the associated function called on retrieval.

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

Dictionary functions are called with an `option` map, `params` from the
dictionary key lookup and any remaining items from the reference tuple that
invoked the function.

This reference lookup:

```clj
(def dictionary
  (m1p.core/prepare-dictionary
   {:songs [:fn/plural "no songs" "one song" "{{:n}} songs"]}
   {:dictionary-fns {:fn/plural pluralize}}))
```

And this lookup:

```clj
(m1p.core/lookup dictionary :songs 0)
```

Will result in `pluralize` being called with an options map, `0` as `params`,
and then the strings `"no songs"`, `"one song"`, and `"{{:n}} songs"`, e.g.:

```clj
(pluralize {} 0 "no songs" "one song" "{{:n}} songs")
```

<a id="fn-str"></a>
### `[:fn/str s]`

One of the built-in [dictionary functions](#dictionary-function) in m1p.
Performs string interpolation on the string `s`. Any occurrence of `{{:k}}` will
be replaced with `:k` from the params passed to `m1p.core/lookup`. If a string
contains an interpolation placeholder that is not provided in `params` on
retrieval, the behavior is defined by the function passed as
`:on-missing-interpolation` in the options map to `m1p.core/interpolate`.

By default missing interpolations will render the string:

```clj
"[Missing interpolation key :k]"
```

In place of the placeholder.

<a id="fn-get"></a>
### `[:fn/get k]`

Returns the key `k` in `params` as passed to `m1p.core/lookup`.

<a id="prepare-dictionary"></a>
### `(m1p.core/prepare-dictionary dictionary opt)`

<a id="lookup"></a>
### `(m1p.core/lookup dictionary k & [data])`

<a id="interpolate"></a>
### `(m1p.core/interpolate opt data)`
