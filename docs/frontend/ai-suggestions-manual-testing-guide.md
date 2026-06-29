# AI Suggestions Manual Testing Guide

Use this guide to test whether AI Suggestions reacts correctly to onboarding, paper-trading behavior, and current holdings.

## Clean Account Or Existing Account?

Use a new account for clean baseline tests. This avoids old onboarding versions, old AI batches, watchlist state, and paper-trading behavior influencing the result.

Use an existing account only when you intentionally want to test persistent behavior. Portfolio reset clears current positions and starts a new paper-trading session, but it does not delete the account's history. The suggestion backend now treats behavior as low-confidence when the portfolio reset is newer than the stored behavior profile, then behavior rebuilds after new BUY/SELL trades.

Always use the Suggestions screen Refresh button only when cooldown allows it. Pull-to-refresh and the header refresh reload cached GET data only.

## Onboarding Answer Sets

Conservative beginner:

| Question | Choose |
| --- | --- |
| If a stock drops by 15% | `risk_reaction_sell_reduce` |
| Price movement comfort | `volatility_low` |
| Main goal | `goal_stable` |
| Investing experience | `experience_beginner` |
| Holding period | `horizon_long` |
| Spread virtual money | `concentration_diversified` |
| Uncomfortable loss level | `loss_tolerance_low` |
| Helpful suggestion style | `guidance_safer_explained` |

Balanced beginner:

| Question | Choose |
| --- | --- |
| If a stock drops by 15% | `risk_reaction_wait_review` |
| Price movement comfort | `volatility_medium` |
| Main goal | `goal_balanced` |
| Investing experience | `experience_beginner` |
| Holding period | `horizon_medium` |
| Spread virtual money | `concentration_balanced` |
| Uncomfortable loss level | `loss_tolerance_medium` |
| Helpful suggestion style | `guidance_balanced_compare` |

Growth/aggressive learner:

| Question | Choose |
| --- | --- |
| If a stock drops by 15% | `risk_reaction_buy_more` |
| Price movement comfort | `volatility_high` |
| Main goal | `goal_growth` |
| Investing experience | `experience_basic` or `experience_intermediate` |
| Holding period | `horizon_short` |
| Spread virtual money | `concentration_focused` |
| Uncomfortable loss level | `loss_tolerance_high` |
| Helpful suggestion style | `guidance_growth_opportunities` |

## Flow 1 - Clean Conservative Baseline

Purpose: prove fresh conservative onboarding is respected without polluted behavior.

1. Create a new beginner account.
2. Complete onboarding with the Conservative beginner answer set.
3. Open Suggestions.
4. Confirm initial load does not auto-refresh. It should call cached `GET /api/stocks/ai-suggestions` only.
5. Tap the Suggestions screen AI refresh button if cooldown allows it.
6. Expected result:
   - Suggested stocks should lean conservative or moderate when stored data allows it, such as KO, JNJ, MSFT, AAPL, or GOOG.
   - Aggressive names such as NVDA, AMD, or TSLA should not dominate all three slots unless the backend explains a data-quality or behavior reason.
   - Reasons should say educational/paper-trading wording, not "buy now" or "best stock".
   - Highlighted phrases should appear only as normal green/red/bold text, never raw tags.
7. Tap each suggestion card body and verify stock detail opens, then back returns to Suggestions.
8. Tap Dismiss and Add to watchlist on a suggestion and verify those buttons do not open stock detail.

## Flow 2 - Reset Boundary And Fresh Retake

Purpose: verify reset and retake do not let stale aggressive behavior silently dominate.

1. Use an existing account that has old paper-trading behavior, or create one and make several aggressive trades first.
2. In Portfolio, reset the portfolio.
3. Retake onboarding with the Conservative beginner answer set.
4. Open Suggestions and use AI refresh when cooldown allows.
5. Expected result:
   - The latest onboarding should matter strongly.
   - If the reset happened after the behavior profile update, old behavior should be treated as stale/low-confidence until new BUY/SELL trades rebuild behavior.
   - Suggestions should be conservative/moderate balanced, not an unchanged aggressive-only list.
   - Batch summary or reasons may mention limited/rebuilding paper-trading behavior in beginner-friendly wording.
6. Make one small aggressive BUY, such as 1 NVDA or 1 AMD.
7. Refresh suggestions after cooldown or use a test account/admin-safe setup where cooldown is available.
8. Expected result:
   - One tiny trade should not completely override conservative onboarding.
   - Aggressive stocks may appear as observation examples, but match scores should stay cautious and reasons should explain the mixed signal.

## Flow 3 - KO Concentration Versus Aggressive Tiny Trades

Purpose: verify a large conservative holding affects suggestions differently from tiny aggressive trades.

1. Use a new or clean account with the Balanced beginner or Conservative beginner answer set.
2. Buy a very large KO position so KO is about 95% to 99% of open paper holdings value.
3. Buy tiny positions in NVDA, AMD, or GOOG, such as quantity 1 each.
4. Open Suggestions and run AI refresh when allowed.
5. Expected result:
   - The prompt/hash should now differ from the same account with no KO holding.
   - Suggestions should not remain identical to the no-KO case when KO dominates holdings.
   - Aggressive stocks should be reduced, lower scored, or explained as higher-risk learning examples.
   - Conservative/moderate names such as JNJ, KO, MSFT, AAPL, or GOOG should have a realistic chance to appear depending on stored snapshot quality.
6. Reset the portfolio.
7. After reset, refresh suggestions when allowed.
8. Expected result:
   - Current portfolio exposure becomes empty.
   - If no new BUY/SELL trades happen, behavior should not pretend the old KO holding is still current.
   - Suggestions may shift again because the current holdings hash changed.

## Flow 4 - Meaningful Aggressive Behavior

Purpose: verify quantity/value matters more than many tiny trades.

1. Use a clean account with the Balanced beginner answer set.
2. Buy one large aggressive position, such as a high-value NVDA or AMD order.
3. Refresh suggestions when allowed.
4. Expected result:
   - The backend should treat the large position as meaningful current exposure.
   - Aggressive suggestions may increase, but reasons must frame them as educational paper-trading examples with risk language.
5. Reset or use a second clean account.
6. Make five tiny BUY trades of quantity 1 in the same aggressive stock.
7. Refresh suggestions when allowed.
8. Expected result:
   - The five tiny trades should not outweigh the single large position case purely because there are more trades.
   - Behavior confidence may remain limited if there are too few distinct symbols.
   - Suggestions should explain limited behavior confidence when relevant.

## What To Record

For each flow, capture:

- Account email or note that it is a new clean account.
- Onboarding answer set.
- Portfolio positions and approximate position percentages.
- Whether portfolio was reset before refresh.
- Whether Suggestions initial load made only GET.
- Refresh time, cooldown status, and whether timeout occurred.
- Top three suggested symbols, match scores, risk labels, and short reasons.
- Whether highlights rendered cleanly without raw tags.
- Whether suggestions changed after holdings or behavior changed.

## Accuracy Notes

Expected suggestions are directional, not fixed symbols. The backend uses current stored market snapshots, delayed data quality, onboarding, behavior confidence, and current holdings exposure. A result is suspicious when the same three aggressive symbols with the same scores appear across materially different states, such as 99% KO holdings versus no KO holdings.
