# m1p: Map interpolation and DIY i18n++

m1p (short for "map" in the tradition of i18n) is a map interpolation library
than can be used for i18n (or just externalizing textual content for a single
language), theming, and similar use cases. Some assembly required. Batteries not
included. Bring your own bling.

## m1p's core value proposition

m1p lets you to loosely couple data processing code from textual content by
placing it in a dictionary, and to loosely couple its content from app data that
will eventually be interpolated into it.

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
- Dictionaries should support declarative interpolation.

## i18n with m1p

The best way to understand m1p (pronounced "meep", or "meep, meep" if you're
really feeling it) is through an example. In many ways, m1p is an i18n library
that isn't really an i18n library. It's very good at retrieving and
"just-in-time processing" values, but it knows nothing about locales,
pluralization, number formatting, and other i18n concerns. It can, however,
learn those things from you. We'll explore how m1p works by using it as a i18n
library.

### Base case

m1p works with [dictionaries](#dictionary) built from plain serializable maps:

<a id="ex1"></a>
```clj
(require '[m1p.core :as m1p])

(def dictionary
  {:header/title "Hello, world!"})

(m1p/interpolate
 {:dictionaries {:i18n dictionary}} ;; 1
 [:div.main
  [:h1 [:i18n :header/title]]])     ;; 2

;;=> [:div.main [:h1 "Hello, world!"]]
```

1. m1p can interpolate from multiple dictionaries at once. This example only has
   one dictionary, the `:i18n` one.
2. `[:i18n :header/title]` is a [reference tuple](#reference-tuple) that refers
   to the key `:header/title` in the `:i18n` dictionary.

### Where it gets interesting

Greeting the world is all well and good, but what if we want to make our
greeting more personal? Well, then we have to fold some data into the template
before folding the result back into our data:

<a id="ex2"></a>
```clj
(m1p/interpolate
 {:dictionaries {:i18n dictionary}}
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]])
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
 {:dictionaries {:i18n dictionary}}
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]])

;;=> [:div.main [:h1 "Hello, Internet!"]]
```

Yay!

`m1p.core/prepare-dictionary` helps dictionaries do interesting things to their
values on retrieval: interpolate data from your program, "pluralize" texts,
format dates, apply gradients to colors - your imagination is the limit. These
transformations are performed with [dictionary
functions](#dictionary-functions).

`[:fn/str "Hello, {{:greetee}}"]` references the built-in dictionary function
[`:fn/str`](#fn-str). You can also register [custom dictionary
functions](#custom-dictionary-functions). When you retrieve values like this,
the associated function will be called with params from the key lookup
(`{:greetee "Internet"}`), the rest of the tuple (e.g. `"Hello, {{:greetee}}"`),
and options (explained later). `:fn/str` performs string interpolation.

If a dictionary value has a placeholder that isn't inside a string, you can use
m1p's other built-in dictionary function, [`:fn/get`](#fn-get), which just gets
parameters:

<a id="ex3"></a>
```clj
(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:span "Hello, "
                   [:strong [:fn/get :greetee]]]}))

(m1p/interpolate
 {:dictionaries {:i18n dictionary}}
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]])

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

<a id="custom-dictionary-functions"></a>
### Custom dictionary functions

Dictionary functions can bring any number of new features to m1p dictionaries.
We'll consider two examples that are commonly used in i18n tooling.

#### Pluralization

Pluralization is a hard problem to solve properly across all languages, but
usually a trivial matter to implement for the handful of languages your app
supports.

Here's how you can add a naive "0, 1, many"-style pluralization helper to a
dictionary:

<a id="ex5"></a>
```clj
(require '[m1p.core :as m1p])

(defn pluralize [opt n & plurals]
  (-> (nth plurals (min (if (number? n) n 0) (dec (count plurals))))
      (m1p/interpolate-string {:n n} opt)))

(def dictionary
  (m1p/prepare-dictionary
   {:songs [:fn/plural "no songs" "one song" "{{:n}} songs"]}
   {:dictionary-fns {:fn/plural pluralize}}))

(m1p/lookup {:dictionary dictionary} :songs 0) ;;=> "no songs"
(m1p/lookup {:dictionary dictionary} :songs 1) ;;=> "one song"
(m1p/lookup {:dictionary dictionary} :songs 4) ;;=> "4 songs"
```

`m1p.core/lookup` performs a single lookup just like the reference tuple
`[dict-k :songs 0]` would do with `m1p.core/interpolate`.

### Date formatters

Here's how you can use dictionary functions to format dates:

<a id="ex6"></a>
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
### `(m1p.core/lookup opt k & [data])`

<a id="interpolate"></a>
### `(m1p.core/interpolate opt data)`
