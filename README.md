copy-trader
==

This is an engine to facilitate copy-trading syndicated trades by other traders over the internet.
YMMV, and you are responsible for trades on your own account.

Dependencies
--
1. Clojure and `make`

Configuration
--
1. Copy `example.config.json` to `config.json` and fill in your API key and adjust settings as desired
1. `leverage` valid values range between 0.5 and 2.0
1. `type` valid values are `paper` and `live`
1. `shorting` should probably be `true` unless you are trading on a cash-only account, like an IRA

Running
--
1. Run `make dev` to start

License
==
AGPLv3