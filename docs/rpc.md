# RPC: the parked idea

Out of scope. Nobody builds from this file; it exists so the idea can be
refined and checked against the codec layer as that layer grows. Agents do
not read it unless asked to.

## What the codec layer must not preclude

Checked whenever `Codec<T>` or the generated artifacts change:

1. Exact length before encoding: `encodedLength(value)`, so a transport can
   `tryClaim` and the codec writes into the claimed region. Already in scope.
2. Reading `schemaId` and `templateId` from a buffer without decoding, so a
   dispatcher can route. A static header peek in the api is enough.
3. Dispatch by template id over a family: a sealed interface's codec that
   decodes to the interface type. Increment 16.
4. Codecs that are instances with no static state, so a session can own one
   per thread. Already so.
5. Per-message codecs that stay usable standing alone, so a generated service
   layer can compose them rather than replace them.

## The idea

Services written against the domain model, with the wire, the handlers, the
callers and the observers all generated from one contract.

- **Contract.** `@RpcService(id)` on an interface, `@RpcMethod(id)` on each
  method, both `uint16` and hand-assigned. Request and response types are
  `@SbeMessage` records or sealed families. `void` is one-way, `R` is unary,
  `Stream<R>` streams. No client or server in the contract: the other
  direction is another contract implemented on the other side.
- **Wire.** One envelope schema owned by sbe-buddy with a reserved schema id:
  `Request(correlationId, serviceId, methodId)` + payload, zero or more
  `Response(correlationId)` + payload, exactly one `Complete(correlationId,
  status)`. Payloads follow the envelope back to back in the same fragment
  with their own header, never nested as var-data. Correlation ids are
  allocated by the sender from its own counter, per session.
- **Generated, in three flavours** (records; typed flyweights; sbe-tool's
  flyweights for the hottest paths): one handler interface per method, an
  effects object as the handler's only output path (one method per response
  leaf, per declared emitted type, plus `complete()` and `fail(status)`), an
  observer as the dual of the effects for folding a log back into events,
  callers, and a dispatcher builder with one typed slot per method and no
  reflection.
- **Errors are values.** Business outcomes are leaves of the response
  family; infrastructure failures are `Complete.status`; a throwing handler
  is a bug reported once to an `ErrorHandler`. The server never throws.
- **Idempotency** is per method and opt-in; the dispatcher dedupes by
  `(session, correlationId)` over a bounded window.
- **On Aeron Cluster** every emission is stamped with the ingress log
  position, so the outcome of a request is a function of the log.

Not planned: streaming requests, obligations on handlers, persistence.
