# Want do
1. Multiple keys listener and tracker registration
2. Additional trackers (like single file tracker, or socket tracker, or network tracker)
3. Separate processor from single tracking type.  
4. Aggregator and listeners chain dependency meta information.
5. Aggregator cycles checker
6. More informative stopped state.
7. More flexible SAX and DOM extensions (Possibly, dividing into separate class).
8. More precise and deterministic test framework to test asynchronous flows.
9. Create own publisher/subscriber implementation, rather than default Java API, since
   some usage flaws were spotted.
10. Performance tests.
11. Concurrency stress tests with jcstress.
12. Test events aggregation more aggressively.