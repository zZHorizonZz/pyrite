# Intercept flows

Run your own code at the moments of a flow, send the codes of flows from your application, and hear of each account
and session that Pyrite creates and ends.

An interceptor is a handler in a chain: it passes the flow on, or stops it. An observer is one handler that hears of an
event after the fact. `Pyrite` takes both.

## Before you begin

- Make Pyrite, and keep the instance that `Pyrite.pyrite` gives. See
  [Serve the services](../deployment/serve-the-services.md).

## Intercept the moments of a flow

An interceptor gets a `FlowContext`: the name of the flow, its definition, its account and the result so far. It calls
`next()` to pass the flow on, or `fail(cause)` to stop it. The flow waits for each interceptor, so an interceptor can
run asynchronous work, such as a call to a fraud check, before it calls either. Call one of the two on every path, or
the call of the client never ends.

| Moment       | Add with                    | Runs                                                                         |
| ------------ | --------------------------- | ---------------------------------------------------------------------------- |
| Flow start   | `addFlowStartInterceptor`   | When `StartFlow` starts a flow, before Pyrite stores it or makes any code    |
| Stage end    | `addStageEndInterceptor`    | Once per answer that meets a stage, before the next stage or the end         |
| Flow end     | `addFlowEndInterceptor`     | When the last stage is met, after the completion handler                     |
| Flow failure | `addFlowFailureInterceptor` | After a wrong answer, or one that the flow refuses, before the call fails    |
| Code         | `addCodeInterceptor`        | For each code of a flow, after Pyrite stores the flow                        |

A flow that is refused is one that is done, expired, at another revision, or out of answers. An answer that the stage
does not take, or that does not parse, fails without the interceptors of failures.

1. Add an interceptor:

   ```java
   pyrite.addFlowStartInterceptor(context -> {
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
   Handler<FlowContext> audit = context -> {
     log.info("{} of {} ended for {}", context.flow(), context.definition(), context.account().orElse("nobody"));
     context.next();
   };
   pyrite.addFlowEndInterceptor(audit);
   pyrite.removeFlowEndInterceptor(audit);
   ```

The interceptors of a moment run in the order that you add them. An interceptor of failures that calls `next()` lets
the call fail as it would have, such as with `PERMISSION_DENIED`; one that calls `fail(cause)` fails it with that cause
instead.

## Replace the declared steps of a flow

The end of every flow starts with the completion handler, which does what the flow declares in `completes`, such as
issuing the session. The interceptors that you add to the end run after it, and see what it put into the result.

1. To run your own steps in place of the declared ones, remove the completion handler, and add your own:

   ```java
   pyrite.removeFlowEndInterceptor(CompletionHandler.create());
   pyrite.addFlowEndInterceptor(context -> {
     audit.record(context.flow(), context.result().getAccount());
     context.next();
   });
   ```

   Every completion handler is equal to every other, so `CompletionHandler.create()` removes the one that Pyrite
   added.

2. To run your own steps before the declared ones, remove the completion handler, add yours, and add
   `CompletionHandler.create()` again after them.

Without the completion handler, a flow ends without its declared steps: a sign-in issues no session, and an enrolment
activates nothing. The result holds the account of the flow either way.

## Send codes from your application

Without a code interceptor, the response of a flow carries each code that it makes, and your application sends it.
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

| Event           | Set with               | Hears of                                                                         |
| --------------- | ---------------------- | -------------------------------------------------------------------------------- |
| Account created | `accountCreateHandler` | Each account that `CreateAccount` creates                                        |
| Account deleted | `accountDeleteHandler` | Each account deleted, through `DeleteAccount` or `DeleteObject` of the access service |
| Session issued  | `sessionIssueHandler`  | Each session that a flow issues                                                  |
| Session ended   | `sessionEndHandler`    | Each session that `EndSession`, or a flow with `END_SESSIONS`, ends              |

```java
pyrite.accountCreateHandler(account -> audit.record("account created", account.getName()))
    .sessionIssueHandler(session -> metrics.increment("sessions"));
```

An observer hears of an event after the fact, so it cannot stop it. A session that expires, or whose account is
deleted, ends without its observer. A handler that throws changes nothing of what happened; Pyrite logs the throw.

## Verify

An interceptor of the start that fails a flow fails `StartFlow` with its status, such as `UNAVAILABLE`, and no flow
is stored. With a code interceptor, `flow.getDeliveriesCount()` is 0 and the interceptor gets each delivery. Each
sign-in calls the session observer once, with the session of `principal.getSession()`.

## Related

- [About accounts and flows](about-accounts-and-flows.md)
- [Verify an email address](verify-an-email-address.md)
- [Serve the services](../deployment/serve-the-services.md)
