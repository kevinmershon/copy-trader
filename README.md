copy-trader
==
This is an engine to facilitate copy-trading syndicated trades by other trusted
traders over the internet.

How it Works
--
This application acts as both a WebSocket client and server on port `51585`.

Client authentication is performed using a 512-bit EC public/private key pair
that you must either generate yourself, or if you are subscribing to syndicated
trades of others, you must receive their `public-key.pem` file and put it in the
project working directory.

Only nodes having the `private-key.pem` file that pairs with the public key may
emit valid trade messages themselves, however every node in the network will
route trade messages to their immediate connections. Because of this, if you are
running your own trade syndication network, be very careful with whom you share
the private key (as in any case with cryptographic signing use cases), as anyone
with the file running this project will be able to dispatch trade signals to the
entire network.

Trade messages are themselves rudimentary, only describing the attributes of the
trade itself: long or short, limit or stop-loss, symbol, type, and price
information.

Disclaimers and Risks
--
1. **You are 100% responsible for any trades made on your accounts, with or
   without this software.**
1. **There is no logic within this system for managing a position once opened.**
1. Once you are in a position, you either need to manage it yourself, or trust
   that the node that dispatched the trade message will accordingly follow-up
   with take-profit and stop-loss adjustments.
1. For very large networks, slippage and missed trades (orders that are opened
   but never filled) are virtually guaranteed, especially for illiquid markets
   (symbols that have low trade volume).
1. If you are joining a third-party network and not hosting your own, keep in
   mind that trusted nodes in the network are almost certainly front-running
   trades they share with the network, for their own personal gain.
   
Generating public/private key pairs
--
These instructions could change depending on your operating system. The
following commands work if you have `openssl` and `ssh` installed:

```
openssl ecparam -genkey -name secp521r1 -noout -out private-key.pem
chmod 400 private-key.pem
ssh-keygen -f private-key.pem -e -m pem > public-key.pem
```

Configuration
--
1. Copy `example.config.json` to `config.json` and fill in your API key and adjust settings as desired
1. `type` valid values are `paper` and `live`
1. `max_positions` valid values above 1, recommended values between 15 and 20
1. `leverage` valid values range between 0.5 and 2.0
1. `risk` is a percentage range between 0.01 and 1.00, recommended values between 3% and 10%
1. `shorting` should probably be `true` unless you are trading on a cash-only account, like an IRA

Build and run locally
--
In all cases you should have `make` / build-essential installed.

### Dependencies
1. Redis running locally on port `6379`
1. Clojure 1.10+ and `rlwrap`

### Running
Run `make dev` to start

Build and run with Docker / Docker-Compose
--
1. `make docker/build-all`
1. `make docker/run-all`

License
==
AGPLv3
