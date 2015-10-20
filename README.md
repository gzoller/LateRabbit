# LateRabbit
Late-Ack RabbitMQ integration for Akka Streams.  Not this is not full-featured at all.
It provides basic functionality.  It's inspired by op-rabbit, where the RabbitControl concept came from,
and from reative-rabbit where the QMessage concept came from.

op-rabbit is super cool, but I was concerned about the approach of wrapping all the Akka Streams primitives in "acked" versions.  The Streams library is still 1.0, meaning it will certainly evolve and change.  I think if Streams were mature/stable the op-rabbit approach would be fantastic and transparent.

So I've opted to wrap my stream messages in a wrapper containing the delivery tag for ack-ing, like reactive-rabbit does.  It's perhaps a less elegant and manual approach but I believe it will better endure Streams' evolution for the time being.

This project seeks a minimal "fusion" of the two concepts.
