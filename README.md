# janus
(Janus was a two-faced Roman god who looked forward to the future and backwards to the past.  His name gives us the name of the month `January`)

A Clojure routing library.  This is my second initiative on this front -the first as a collaborator on the `wire` library.  Inspiration has been taken from:

 * [wire](https://github.com/mwmitchell/wire)
 * [bidi](https://github.com/juxt/bidi)
 * [compojure](https://github.com/weavejester/compojure)
 * [pedestal](https://github.com/pedestal/pedestal)

## Context
The interpretation of "URL"s is rife with misunderstandings, competing standards and imcompatible interpretations.  in the context of janus, a URI is the name by which a local resource is referenced on the web.

The way I see it, there are two jobs for a router

1. Identify inbound routes -this could be satisfied by a tree of identifiers associated with regular expressions.
2. Generate outbound routes -this could be satisfied by a tree of identifiers associated with `format` strings.

These are the core functions of a "pure" routing engine and they should be powerful and generally uncompromised by secondary concerns.

## Design Goals
 1. The path components of URIs and are assumed (per [RFC 3986](https://en.wikipedia.org/wiki/Uniform_Resource_Identifier#Syntax)) to be a string of `/`-separated and [url-encoded](https://en.wikipedia.org/wiki/Percent-encoding) segments.
 1. The route abstraction handles encoding and destructuring transparently; it models the URI
    as a sequence of URL-decoded string segments.
 1. Route matching is decomplected from handler dispatching per the Ring model.
 1. For all but the most complex routes, pure data represents the entire route definition.
 1. Forward (generation) and backward (identification) routing are inverses of each other.
 1. Protocols are used for segment matching and segment generation.
 1. Dispatching is based on route matching only.  There is no support for dispatching based on other attributes of an HTTP request, such as method.
 1. Routing works the same in Clojure and Clojurescript.
 1. A single compact data structure should represent all that is necessary for matching, generating and even dispatching.  For common route components (constant strings, leaf nodes, etc), the syntax should be particularly compact.

## Comparisons
 * Compared to Compojure, janus offers routes-as-data, (theoretical) Clojurescript compatibility, invertible routes, independent route identification and dispatching, and protocol-based extensibility.   On the downside, janus offers no support for dispatching based on HTTP method.
 * Compared to bidi, janus offers (generally) invertible routes, independent route identification and dispatching, and a higher-level abstraction that deals with encoding and destructuring.  On the downside, janus offers no support for dispatching based on HTTP method.
 * Compared to wire, janus offers invertible routes, (theoretical) Clojurescript compatibility and protocol-based extensibility.  On the downside, janus offers no support for dispatching based on HTTP method.

## Usage

See janus.route and janus.ring namespaces for usage notes.

## License

Copyright © 2017 Chris Hapgood

Distributed under the Eclipse Public License version 1.0.
