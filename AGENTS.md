# AGENTS.md

This file provides guidance to coding agents (Claude Code, Codex, ...) when working with code in this repository.

## Commands

```bash
# Build and run the tests (what CI runs, .github/workflows/maven.yml, JDK 17)
mvn -B verify

# Tests only / a single test method (there is one test class)
mvn test
mvn test -Dtest=SmsTest#aBlacklistedPhoneIsNeverSent

# Run the module on its own: http://localhost:8080, erupt / erupt
mvn spring-boot:run
```

Java 17, system `mvn` (no wrapper). The parent pom is `spring-boot-starter-parent` (the Spring Boot version
erupt is built on), not `xyz.erupt:erupt`: the erupt parent published to Maven Central is flattened and
carries no dependency management. The erupt version is the `erupt.version` property; erupt 2.4.0 is on
Maven Central, so a fresh clone builds without the erupt checkout.

## What this is

A library, not an application: SMS across vendors for erupt, built on SMS4J (`sms4j-javase-plugin`).
A host application adds the dependency and gets an **SMS** menu (Channels, Templates, Messages,
Verification codes, Blacklist) plus a Java API, `EruptSms`, to send from its own code. The module ships
no HTTP endpoint. `erupt-spring-boot-starter` is `provided` (the host brings the erupt stack);
`spring-boot-maven-plugin` only serves `spring-boot:run`, repackaging is off.

## Architecture

- **Registration.** `EruptSmsAutoConfiguration` is listed in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, implements
  `EruptModule` and builds the menus in `initMenus()` (root `$sms`). Its `@ComponentScan` excludes
  `@SpringBootApplication` classes, because the standalone launcher `EruptSmsApplication` lives in the
  same package. The launcher sets default properties only: an H2 file database under `./h2`.
- **Models (`model/`).** JPA entities on `e_sms_*` tables: `SmsChannel` (a vendor account, one of them the
  default), `SmsTemplate`, `SmsMessage`, `SmsCode`, `SmsBlacklist`. Vendor credentials are fields of
  `SmsChannel`, never configuration: refer to them by field name.
- **Workspace.** `core/SmsScope` is an `@EruptScope` form with one field, the channel, picked in the page
  header and sent with each request in the `erupt-scope` header. Templates, messages and codes declare
  `@EruptScope(SmsScope.class)`, so they are filtered by it and new rows land in it; `SmsContext` reads
  the channel of the current request.
- **Channels are rows, not files.** `core/SmsChannels` builds one SMS4J `SmsBlend` per enabled channel at
  startup and whenever a channel is saved (`proxy/SmsChannelDataProxy`), and drops a disabled one.
  `SmsChannels#put` plugs in a blend SMS4J does not ship; the tests use it for their stand-in.
- **Sending.** `core/SmsSender` is the single path: blacklist check, the template's parameters in their
  order, the outcome and the vendor's answer on the `SmsMessage` row. Saving a message in the admin sends
  it (`proxy/SmsMessageDataProxy`), the row operation resends (`handler/SmsMessageHandler`), and
  `core/EruptSms` is the fluent API for host code (`template(code).to(...).params(...).send()`,
  `via(channel)`, `code(phone).send()` / `.verify(code)`). A failed send from code throws and still
  leaves a FAILED row.
- **Templates.** A template saved without parameters takes them from the `${name}` placeholders of its
  content, in order of appearance (`proxy/SmsTemplateDataProxy`). The order is editable because some
  vendors read parameters by position and others by name.
- **Verification codes.** Sent through the channel's enabled template of kind *Verification code*; rate
  limited per phone per day (a channel setting) with a wait between two codes, valid once.
  `EruptSmsProperties`: `erupt.sms.code-length`, `erupt.sms.code-ttl-seconds`, `erupt.sms.resend-seconds`.
- **i18n.** English keys, translated in `src/main/resources/i18n/erupt-sms.i18n.csv`; runtime error
  messages use `sms.*` keys through `I18nTranslate.$translate`.

## Tests

`SmsTest` is a `@SpringBootTest` on a random port and an in-memory H2
(`src/test/resources/application.yml`), with a recording `SmsBlend` standing in for the vendor. It
covers workspace scoping, parameter order, the blacklist, codes and the `EruptSms` API.

## Behaviour worth knowing (from README.md)

- Templates and signatures are approved at the vendor; a template here records the vendor's id and text.
- Delivery reports are not collected: the status is the vendor's answer to the send call.
