# janus
(Janus was a two-faced Roman god who looked forward to the future and backwards to the past.  His name gives us the name of the month `January`)

A Clojure routing library.  This is my second initiative on this front -the first as a collaborator on the `wire` library.  Inspiration has been taken from:

 * [wire](https://github.com/mwmitchell/wire)
 * [bidi](https://github.com/juxt/bidi)
 * [compojure](https://github.com/weavejester/compojure)
 * [pedestal](https://github.com/pedestal/pedestal)

## Context
The interpretation of "URL"s is rife with misunderstandings, competing standards and imcompatible interpretations.  In the context of janus, a URI is the name by which a local resource is referenced on the web.

The way I see it, there are two jobs for a router

1. Identify inbound routes -this could be satisfied by a tree of identifiers associated with regular expressions.
2. Generate outbound routes -this could be satisfied by a tree of identifiers associated with `format` strings.

These are the core functions of a "pure" routing engine and they should be powerful and generally uncompromised by secondary concerns.

## Design Goals
 1. The path components of URIs are assumed (per [RFC 3986](https://en.wikipedia.org/wiki/Uniform_Resource_Identifier#Syntax)) to be a string of `/`-separated and [url-encoded](https://en.wikipedia.org/wiki/Percent-encoding) segments.
 1. The route abstraction handles encoding and destructuring transparently; it models the URI
    as a sequence of URL-decoded string segments.
 1. Route matching is decomplected from handler dispatching per the Ring model.
 1. For all but the most complex routes, pure data represents the entire route definition.
 1. Forward (generation) and backward (identification) routing are inverses of each other.
 1. Protocols are used for segment matching and segment generation.
 1. Dispatching is based on route matching only.  There is no support for dispatching based on other attributes of an HTTP request, such as method.  Other libraries, such as [liberator](https://github.com/clojure-liberator/liberator), do an excellent job of managing HTTP method-based processing _after_ routing.
 1. Routing works the same in Clojure and Clojurescript.
 1. A single compact data structure should represent all that is necessary for matching, generating and even dispatching.  For common route components (constant strings, leaf nodes, etc), the syntax should be particularly compact.
 1. Both forward and backwards routing can be performed in the scope of an existing route.

## Comparisons
 * Compared to Compojure, janus offers routes-as-data, (theoretical) Clojurescript compatibility, invertible routes, independent route identification and dispatching, and protocol-based extensibility.   On the downside, janus offers no support for dispatching based on HTTP method.
 * Compared to bidi, janus offers (generally) invertible routes, independent route identification and dispatching, and a higher-level abstraction that deals with encoding and destructuring.  On the downside, janus offers no support for dispatching based on HTTP method.
 * Compared to wire, janus offers invertible routes, (theoretical) Clojurescript compatibility and protocol-based extensibility.  On the downside, janus offers no support for dispatching based on HTTP method.

## Usage

See janus.route and janus.ring namespaces for usage notes.

### Route Format

The canonical format for a route is a recursive sequence of two elements, the second of which is a sequence of three elements:

    [identifiable [as-segment dispatchable routes]]

The outer sequence can be viewed as a pair, and is satisfied by a Clojure map entry.  In fact, maps are the preferred way of representing a collection of routes:

    {identifiable1 [as-segment1 dispatchable1 routes1
     identifiable2 [as-segment2 dispatchable2 routes2
	 ...
     identifiableN [as-segmentN dispatchableN routesN}

Only when the identification of route segments is not guaranteed to be unique does the order of routes become meaningful.  At that point, routes can be represented by a sequence of pairs:

    [[identifiable1 [as-segment1 dispatchable1 routes1]
     [identifiable2 [as-segment2 dispatchable2 routes2]
	 ...
     [identifiableN [as-segmentN dispatchableN routesN]]

Routes are organized as a tree: there is one root route and all child routes have exactly one parent.

The elements of a route are:
 * `identifiable` is any instance of `clojure.lang.Named`.  This includes keywords and symbols.  Keywords should be used when the matching of the route yields useful information.  Symbols should be used for "constant" routes.
 * `as-segment` is anything satisfying `janus.route.AsSegment`, which includes strings, keywords, regexes, functions and others.  The role of `as-segment` is twofold: to match inbound route segements and to generate outbound route segements.  The semantics of each are as follows:

 ** `javal.lang.String` matches itself only, generates itself always.
 ** `clojure.lang.Keyword` matches its name only, generates its name always.
 ** `java.util.regex.Pattern` FIXME: Document
 ** `clojure.lang.PersistentVector` FIXME: Document
 ** `clojure.lang.Fn` FIXME: Document

 * `dispatchable` is anything satisfying `janus.route.Dispatchable`, which includes functions, vars and instances of `clojure.lang.Named`.  FIXME: Document better, why is protocol not enforced specifically during normalization?
 * `routes` is a recursive seqable collection of child routes.

While the canonical format for routes is useful for understanding the full capabilities of routing in janus, there are many abbreviated representations that are acceptable.  There is no run-time performance penalty for expressing routes in any abbreviated format.

 1. Only the `identifiable` is required to represent a route.  If the second element of the pair is missing or nil, default values are assigned based on the identifiable.  For example:

        [:root nil] => [:root [nil :root {}]]
        [:root] => [:root [nil :root {}]]

  Of course suppressing the second element is not possible when using a map to represent a sequence of routes.

 1. 

## License

Copyright © 2017 Chris Hapgood

Distributed under the Eclipse Public License version 1.0.
