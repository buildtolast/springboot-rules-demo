# GENERATE: src/main/java/com/codrite/ruleaudit/demo/DemoMessages.java

Generates demo event JSON strings per the contract. Package
com.codrite.ruleaudit.demo.
- public final class DemoMessages, private constructor.
- public static java.util.List<String> generate(int count):
  Cycle deterministically through these FOUR templates by index (i % 4), so the
  output mixes all outcomes and size == count exactly:

    index%4 == 0  -> MATCHED template (high amount / EU):
        {"amount":5000,"region":"EU","tier":"standard","flagged":false}
        Vary the amount a little by index, e.g. amount = 2000 + (i % 5) * 1000,
        keep region "EU".
    index%4 == 1  -> MATCHED template (premium / flagged):
        {"amount":150,"region":"US","tier":"premium","flagged":true}
    index%4 == 2  -> UNMATCHED template (matches none of the rules) - use EXACTLY:
        {"amount":150,"region":"LATAM","tier":"gold","flagged":false}
    index%4 == 3  -> MALFORMED (drives ERRORED). Alternate between two:
        "{bad json"   and   "{\"amount\":}"

  Use String.format for the varied amount. Return the list of `count` strings.
- Output exactly one top-level class.
