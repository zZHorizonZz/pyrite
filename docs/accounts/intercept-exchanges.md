# Intercept exchanges

Run your own code at the moments of an exchange, send the codes of exchanges from your application, and hear of each
account and session that Pyrite creates and ends.

An interceptor is a handler in a chain: it passes the exchange on, or stops it. An observer is one handler that hears of
an event after the fact. `Pyrite` takes both.

## Before you begin

- Make Pyrite, and keep the instance that `Pyrite.pyrite` gives. See
  [Serve the services](../deployment/serve-the-services.md).

## Intercept the moments of an exchange

An interceptor gets a `ExchangeContext`: the name of the exchange, its definition and its account. It calls `next()` to
pass the exchange on, or `fail(cause)` to stop it. The exchange waits for each interceptor, so an interceptor can run
asynchronous work, such as a call to a fraud check, before it calls either. Call one of the two on every path, or the
call of the client never ends.

| Moment           | Add with                        | Runs                                                                               |
| ---------------- | ------------------------------- | ---------------------------------------------------------------------------------- |
| Exchange start   | `addExchangeStartInterceptor`   | When `StartExchange` starts an exchange, before Pyrite stores it or makes any code |
| Stage end        | `addStageEndInterceptor`        | Once per answer that meets a stage, before the next stage or the end               |
| Exchange end     | `addExchangeEndInterceptor`     | When the last stage is met, before the exchange does what it declares              |
| Exchange failure | `addExchangeFailureInterceptor` | After a wrong answer, or one that the exchange refuses, before the call fails      |
| Code             | `addCodeInterceptor`            | For each code of an exchange, after Pyrite stores the exchange                     |

An exchange that is refused is one that is done, expired, at another revision, or out of answers. An answer that the
stage does not take, or that does not parse, fails without the interceptors of failures.

1. Add an interceptor:

   ```java
   pyrite.addExchangeStartInterceptor(context -> {
     if (signInsClosed()) {
       context.fail(new PyriteException(GrpcStatus.UNAVAILABLE, "Sign-ins are closed for maintenance."));
     } else {
       context.next();
     }
   });
   ```

   A `PyriteException`, given to `fail` or thrown, fails the call with its status and message. Any other cause fails
   the call with `INTERNAL`.

2. To remove an interceptor, keep a reference, and pass it to the matching `remove` method:

   ```java
   Handler<ExchangeContext> audit = context -> {
     log.info("{} of {} ended for {}", context.exchange(), context.definition(), context.account().orElse("nobody"));
     context.next();
   };
   pyrite.addExchangeEndInterceptor(audit);
   pyrite.removeExchangeEndInterceptor(audit);
   ```

The interceptors of a moment run in the order that you add them. An interceptor of failures that calls `next()` lets
the call fail as it would have, such as with `PERMISSION_DENIED`; one that calls `fail(cause)` fails it with that cause
instead.

## Stop an exchange before it completes

The interceptors of the end run when the last stage is met, before the exchange does what its declaration completes
with, such as issuing a session or activating a factor. An interceptor of the end that calls `fail(cause)` stops the
exchange before any of it, so a refused sign-in leaves no session, and the handlers of sessions hear of none.

1. Add an interceptor of the end that asks your own rule:

   ```java
   pyrite.addExchangeEndInterceptor(context -> approvals.allowed(context.account().orElseThrow())
       .onComplete(allowed -> {
         if (allowed.succeeded() && allowed.result()) {
           context.next();
         } else {
           context.fail(new PyriteException(GrpcStatus.PERMISSION_DENIED, "The account is waiting for approval."));
         }
       }));
   ```

2. To act on what the exchange did, such as the session that it issued, use the response of the last answer, or an
   observer of sessions. See [Observe accounts and sessions](#observe-accounts-and-sessions).

## Send codes from your application

Without a code interceptor, the response of an exchange carries each code that it makes, and your application sends it.
See [Verify an email address](verify-an-email-address.md).

With a code interceptor, Pyrite gives each code to the interceptors, and the response carries none. Your callers then
never see a code, so a caller that is not the person cannot read one out of a response.

```java
pyrite.addCodeInterceptor(context -> {
  Delivery delivery = context.delivery();
  mailer.send(delivery.getTo(), "Your code is " + delivery.getCode())
      .onSuccess(sent -> context.next())
      .onFailure(failure -> context.fail(new PyriteException(GrpcStatus.UNAVAILABLE, "The code cannot be sent.")));
});
```

`delivery.getKind()` tells how to send the code, such as `email` or `phone`, and `delivery.getExpireTime()` when it
expires, on the clock of the database. An interceptor that fails stops the call with its cause.

## Observe accounts and sessions

An observer is one handler per event. Setting one replaces the one before, and `null` sets none.

| Event           | Set with               | Hears of                                                                              |
| --------------- | ---------------------- | ------------------------------------------------------------------------------------- |
| Account created | `accountCreateHandler` | Each account that `CreateAccount` creates                                             |
| Account deleted | `accountDeleteHandler` | Each account deleted, through `DeleteAccount` or `DeleteObject` of the access service |
| Session issued  | `sessionIssueHandler`  | Each session that an exchange issues                                                  |
| Session ended   | `sessionEndHandler`    | Each session that `EndSession`, or an exchange with `END_SESSIONS`, ends              |

```java
pyrite.accountCreateHandler(account -> audit.record("account created", account.getName()))
    .sessionIssueHandler(session -> metrics.increment("sessions"));
```

An observer hears of an event after the fact, so it cannot stop it. A session that expires, or whose account is
deleted, ends without its observer. A handler that throws changes nothing of what happened; Pyrite logs the throw.

## Verify

An interceptor of the start that fails an exchange fails `StartExchange` with its status, such as `UNAVAILABLE`, and no
exchange is stored. An interceptor of the end that fails a sign-in fails `ContinueExchange` with its status, and the
session observer hears of no session. With a code interceptor, `exchange.getDeliveriesCount()` is 0 and the interceptor
gets each delivery. Each sign-in calls the session observer once, with the session of `principal.getSession()`.

## Related

- [About accounts and exchanges](about-accounts-and-exchanges.md)
- [Verify an email address](verify-an-email-address.md)
- [Serve the services](../deployment/serve-the-services.md)
