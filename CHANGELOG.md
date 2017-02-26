# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).  In the spirit of Rich Hickey's "Spec-ulation" keynote address at the 2016 Clojure/conj, no backwards-incompatible changes will be made to any public var after version 1.0.0.

Note that versioning is managed using [lein-v](https://clojars.org/com.roomkey/lein-v).

## [Unreleased]
### Added
- Custom printing of Router type to suppress.
- Add support for `parent` method in Routable protocol.
- Add `Content-Type` header for 404/Not-Found response.
### Changed
- Only merge route-params with keyword identifiers into `:params`
- Adjust string and regex AsSegment behavior
### Removed
### Fixed
- Fixed bug in `root` method of Router's implementation of Routable protocol.
## 0.4.0 - 2017-02-22
### Added
### Changed
### Removed
### Fixed
- Assoc route params onto request at moment of dispatch, not earlier.
- Treat vars like fns during route normalization.
## 0.3.1 - 2017-02-22
### Added
- Unidentifiable routes are properly handled and ultimately result in a Ring 404 Not-Found response.
### Changed
- Ring identifier middleware takes an instance of janus.route.Router, not routes.
### Removed
### Fixed
## 0.3.0 - 2017-02-22
### Added
### Changed
- Prioritize handlers fn in normalization
### Removed
### Fixed
## 0.2.0 - 2017-02-21
### Added
- Allow dispatchable/handler directly in route tree.
### Changed
- Abstract routing into two primary protocols and implement with janus.route.Router type
- Use zippers for managing navigation through route tree.
## 0.1.0 - 2017-02-08
### Added
- Initial functionality for identifying & generating, plus basic ring identify/dispatching handler

[Unreleased]: https://github.com/cch1/janus/compare/0.1.0...HEAD
[0.1.0]: https://github.com/cch1/janus/compare/0.1.0...0.1.1
