# janus
(Janus was a two-faced Roman god who looked forward to the future and backwards to the past.  His name gives us the name of the month `January`)

A Clojure routing library.  This is my second initiative on this front -the first as a collaborator on the `wire` library.  Inspiration has been taken from:

 * [wire](https://github.com/mwmitchell/wire)
 * [bidi](https://github.com/juxt/bidi)
 * [compojure](https://github.com/weavejester/compojure)
 * [pedestal](https://github.com/pedestal/pedestal)

## Context
For something that web developers understand intuitively, the exact interpretation of "URL"s is rife with misunderstandings, competing standards and imcompatible interpretations.  In an effort to be explicit, for janus a URI is the [RFC 3986](https://en.wikipedia.org/wiki/Uniform_Resource_Identifier#Syntax)-compliant name by which a local resource is referenced on the web.

There are two primary requirements for a router:

1. Identify inbound routes -this could be satisfied by a tree of identifiers associated with regular expressions.
2. Generate outbound routes -this could be satisfied by a tree of identifiers associated with `format` strings.

These are the core functions of a "pure" routing engine and they should be powerful and generally uncompromised by secondary concerns.

## Design Goals
 1. The path components of URIs are assumed (per [RFC 3986](https://en.wikipedia.org/wiki/Uniform_Resource_Identifier#Syntax)) to be a string of `/`-separated and [url-encoded](https://en.wikipedia.org/wiki/Percent-encoding) segments (note that percent-encoding is not the same thing as RFC-compatible url-encoding, notably in the treatment of `+`).
 1. The route abstraction handles encoding and destructuring transparently; it models the URI as a sequence of URL-decoded string segments.
 1. Route identification is decomplected from dispatching to the handler.
 1. For all but the most complex routes, pure data can represent the entire route definition.
 1. Forward (generation) and backward (identification) routing are (with appropriate care) inverses of each other.
 1. Protocols are used for most conceptual tasks, e.g. segment matching/generation and dispatching.
 1. Dispatching is based on route matching only.  There is no support for dispatching based on other attributes of an HTTP request, such as method.  Other libraries, such as [liberator](https://github.com/clojure-liberator/liberator), do an excellent job of managing HTTP method-based processing _after_ routing.
 1. Routing works the same in Clojure and Clojurescript.
 1. A single compact data structure should represent all that is necessary for matching, generating and even dispatching.  For common route components (constant strings, leaf nodes, etc), the syntax should be particularly compact.
 1. Both forward and backwards routing can be performed in the scope of a parent route.

## Comparisons
 * Compared to Compojure, janus offers routes-as-data, Clojurescript compatibility, invertible routes, independent route identification and dispatching, and protocol-based extensibility.   On the downside, janus offers no support for dispatching based on HTTP method.
 * Compared to bidi, janus offers (generally) invertible routes, independent route identification and dispatching, and a higher-level abstraction that deals with encoding and destructuring.  On the downside, janus offers no support for dispatching based on HTTP method.
 * Compared to wire, janus offers invertible routes, Clojurescript compatibility and protocol-based extensibility.  On the downside, janus offers no support for dispatching based on HTTP method.

## Usage

See janus.route and janus.ring namespaces for usage notes.

### Route Format

The canonical data format for a route is a recursive tuple of two elements, the second of which is a three-tuple:

    [identifiable [as-segment dispatchable routes]]

The outer sequence can be viewed as a pair, and is satisfied by a Clojure map entry.  In fact, maps are the preferred way of representing a collection of mutually-exclusive routes:

    {identifiable1 [as-segment1 dispatchable1 routes1]
     identifiable2 [as-segment2 dispatchable2 routes2]
	 ...
     identifiableN [as-segmentN dispatchableN routesN]}

Only when the identification of route segments is not guaranteed to be unique does the order of routes become meaningful.  At that point, routes can be represented by a sequence of pairs:

    [[identifiable1 [as-segment1 dispatchable1 routes1]
     [identifiable2 [as-segment2 dispatchable2 routes2]
	 ...
     [identifiableN [as-segmentN dispatchableN routesN]]

Routes are organized as a tree: there is one root route (which can most easily be represented as a vector tuple) and all child routes have exactly one parent.

The elements of a route are:

1. `identifiable` is any instance of `clojure.lang.Named`.  This includes keywords and symbols.  Keywords should be used when the matching of the route yields useful information.  Symbols should be used for "constant" routes.  This convention is leveraged in janus' ring support namespace.
2. `as-segment` is anything satisfying `janus.route.AsSegment`, which includes strings, keywords, regexes, functions and others.  It is easy to extend AsSegment to accommodate custom interpretations.  The role of `as-segment` is twofold: to match inbound route segements (yielding route parameters) and to generate outbound route segements.  The default semantics of each are as follows:
   * `javal.lang.String`: Matches itself only, returning itself as a route parameter.  Generates itself always.
   * `clojure.lang.Keyword`: Matches its name only, returning its name as a route parameter.  Generates its name always.
   * `java.lang.Boolean`: Matches when true, never when false, and returns inbound segment.  Generates its single string parameter (such as the inbound segment) as the outbound segment.
   * `java.util.regex.Pattern`: Matches when regex matches inbound segment and returns result of regex match as route parameter(s).  Generates either the single result of matching inbound segment or the concatenation of a capture group-matched result.
   * `clojure.lang.Fn`: Matches when function applied to inbound segment returns a truthy result and returns the result as the route parameter.  Generates that same result as the outbound route segment.
   * `clojure.lang.PersistentVector`: Matches when first element (an AsSegment) matches.  Generates what second element (an AsSegment) generates.  Note that the result of matching with the first element becomes the route parameter(s) that are provided as arguments to the second.

3. `dispatchable` is anything satisfying `janus.route.Dispatchable`, which includes functions, vars and instances of `clojure.lang.Named`.  FIXME: Document better, why is protocol not enforced specifically during normalization?
4. `routes` is a recursive seqable collection of child routes.

While the canonical format for routes is useful for understanding the full capabilities of routing in janus, there are many abbreviated representations that are acceptable.  There is no run-time performance penalty for expressing routes in any abbreviated format as all routes are conformed to data types during router construction.

* Only the `identifiable` is required to represent a route.  If the second element of the pair is missing or nil, default values are assigned based on the identifiable.  For example:

        [:root nil] => [:root [nil :root {}]]
        [:root] => [:root [nil :root {}]]
   Of course suppressing the second element is not possible when using a map to represent a sequence of routes.

* If the second element of the route pair is missing components, they are either inferred from the identifier or default values are supplied.

## License

Copyright © 2020 Chris Hapgood

Distributed under the Eclipse Public License version 1.0.
