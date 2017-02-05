# janus
(Janus was a two-faced Roman god who looked forward to the future and backwards to the past.  His name gives us the name of the month `January`)

A Clojure routing library.  This is my second initiative on this front -the first being a collaborator on the `wire` library.  Inspiration has been taken from:

 * [wire](https://github.com/mwmitchell/wire)
 * [bidi](https://github.com/juxt/bidi)
 * [compojure](https://github.com/weavejester/compojure)

## Design Goals
 1. Routes are assumed (per RFC ZXXX) to be a string of `/`-separated and URL-encoded segments.
 1. The route abstraction handles encoding and destructuring transparently; it models the URI
    as a sequence of URL-decoded string segments.
 1. Route matching is decomplected from handler dispatching in the Ring model.
 1. Most Route definitions can be represented as pure data structures.
 1. For all but the most complex routes, pure data represents the entire route definition.
 1. Forward (generation) and backward (matching) routing are inverses of each other.
 1. Protocols are used for segment matching and segment generation.
 1. Routing supports matching/generating paths only.  There is no support for dispatching
    based on other attributes of an HTTP request.
 1. Routing works the same in Clojure and Clojurescript.

## Comparisons
 * Compared to Compojure, janus offers routes-as-data, (theoretical) Clojurescript compatibility, invertible routes, independent route identification and dispatching, and protocol-based extensibility.   On the downside, janus offers no support for dispatching based on HTTP method.
 * Compared to bidi, janus offers (generally) invertible routes, independent route identification and dispatching, and a higher-level abstraction that deals with encoding and destructuring.  On the downside, janus offers no support for dispatching based on HTTP method.
 * Compared to wire, janus offers invertible routes, (theoretical) Clojurescript compatibility and protocol-based extensibility.  On the downside, janus offers no support for dispatching based on HTTP method.

## Usage

See janus.core and janus.ring namespaces for usage notes.

## License

Copyright © 2017 Chris Hapgood

Distributed under the Eclipse Public License version 1.0.
