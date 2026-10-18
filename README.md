# erupt-sms

SMS across vendors on [erupt](https://www.erupt.xyz), built on [SMS4J](https://sms4j.com). Add the dependency, register a channel with the vendor's keys, and an **SMS** menu appears with templates, sending with a log, verification codes and a blacklist. One channel is picked in the page header and every page follows it.

```xml
<dependency>
    <groupId>xyz.erupt</groupId>
    <artifactId>erupt-sms</artifactId>
    <version>${erupt.version}</version>
</dependency>
```

Needs erupt 2.4.0 or newer. H2 comes along as the zero-config database; set `spring.datasource.*` to use another.

## Try it

```bash
mvn spring-boot:run
# http://localhost:8080  erupt / erupt
```

No vendor account yet? Add a channel with the vendor **Console**: it sends nothing, logs every message and reports it as sent, so templates, messages and verification codes can be tried end to end. `ConsoleSms.messages()` holds the last 200 for a test to read.

## What you get

| Menu | What |
|---|---|
| Channels | a vendor account, one of them the default for code that names none: the Console vendor for trying things out, Alibaba Cloud, Tencent Cloud, Huawei Cloud, Baidu, JD, CTYun, Qiniu, NetEase, Yunpian, UniSMS, Cloopen, Emay, Chuanglan, Jiguang, Submail and the rest SMS4J supports; vendor-specific settings go in as `key=value` lines |
| Templates | the templates approved at the vendor, each with a code the application sends by; the `${name}` placeholders of the content become the parameters, in the vendor's order |
| Messages | saving one sends it to one phone or many; the row keeps the rendered text, the outcome and the vendor's answer; resend from the row |
| Verification codes | every code issued through the API below, with its outcome |
| Blacklist | phones never sent to, on any channel |

## The Workspace

The header shows a **Workspace** picker with the channel. Templates, messages and codes are filtered by it and new rows land in it. The pick travels with every request as plain JSON (`erupt-scope` header) and the server checks it against the channels the user is offered. This is erupt's `@EruptScope` feature; `SmsScope` is a plain erupt form.

## Sending from code

The host application sends through `EruptSms`. A template is named by its **code** (the one typed on the template, such as `ORDER_SHIPPED`), parameters by the fields of a record, and the **default channel** is used unless another is named:

```java
@Resource
private EruptSms sms;

record OrderShipped(String name, String order) {}

sms.template(Templates.ORDER_SHIPPED).to("13800000000").params(new OrderShipped("Ann", "A-1")).send();
sms.template("ORDER_SHIPPED").to(phones).param(OrderShipped::name, "Ann").param(OrderShipped::order, "A-1").send();
sms.via("Marketing").template(Templates.SALE).to(phones).send();

sms.code("13800000000").send();
sms.code("13800000000").verify("482913");
```

`Templates` is any enum of the host application whose constant names are the codes. Every send goes the way the admin's own does: blacklist, the template's parameter order, a row in Messages with the outcome. A failed send throws and leaves a FAILED row.

## Verification codes

`sms.code(phone).send()` goes out through the channel's enabled template of kind *Verification code*. Its `${code}` and `${minutes}` (the validity) are filled in; any other parameter of the template is given with `.param(...)` or `.params(record)`, as for a message. Each phone gets at most *Codes per phone per day* (set on the channel), with a wait between two codes; a code is valid for a while and good for one check with `sms.code(phone).verify(code)`. The host application exposes these to its own pages however it likes; the module ships no HTTP endpoint.

```yaml
erupt:
  sms:
    code-length: 6
    code-ttl-seconds: 300
    resend-seconds: 60
```

## Notes

- **Templates and signatures are approved at the vendor.** The template here records the vendor's id and text; its parameters are the `${name}` placeholders, filled in from the content and reorderable, because vendors such as Tencent read them by position while Alibaba reads them by name. A message supplies a value per parameter.
- **Channels are rows, not files.** Each enabled channel becomes one SMS4J `SmsBlend` at startup and whenever it is saved; a disabled channel is dropped. A vendor SMS4J does not ship can still be plugged in through `SmsChannels#put`.
- **Delivery reports** differ per vendor and are not collected; the status is the vendor's answer to the send call.

## Tests

`mvn test` runs the module with a recording `SmsBlend` standing in for the vendor, on an in-memory H2.
