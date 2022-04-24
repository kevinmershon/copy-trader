Ameritrade Traders
==

Caveats
--
Firstly, Ameritrade **ONLY** has live mode trading, despite ThinkOrSwim
supporting paper trading.

Accounts must have the Margin Trading feature enabled to short.

IRAs and Securities-Backed Collateral accounts cannot have margin and so shorting must be disabled to trade against those.

You may only have one application ID per account and that means trading all your accounts in Ameritrade (if you have multiple) is not currently supported.

Configuration
--

1. Run the copy-trader
1. Take note of your TD Ameritrade Account ID. It's a number.
1. Read https://developer.tdameritrade.com/content/authentication-faq and
   https://developer.tdameritrade.com/content/simple-auth-local-apps
1. Register a Developer account at https://developer.tdameritrade.com

    Your developer account **IS NOT THE SAME** as your tdameritrade.com or thinkorswim.com account.

1. Create an Application, specifying the `redirect URL` as
   `http://localhost:51585/ACCOUNT_ID/authorize` where `ACCOUNT_ID` is your TD
   Ameritrade Account ID from step 1.
   
1. Successfully creating an application will give you the Application ID.
   
1. Navigate your web browser to 
   https://auth.tdameritrade.com/auth?response_type=code&redirect_uri=http%3A%2F%2Flocalhost%3A51585%2Fauthorize%2FACCOUNT_ID&client_id=APPLICATION_ID%40AMER.OAUTHAP
   where `ACCOUNT_ID` is your TD Ameritrade Account ID from step 1 and
   `APPLICATION_ID` is your Application ID from the previous step.

1. After logging in and authorizing the app, it should redirect you back to your
   copy-trader instance, but the URL may have `https` instead of `http` at the
   beginning. If so, delete the `s` and load the URL and it should authenticate
   correctly and send you to a page saying so.
