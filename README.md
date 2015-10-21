# LateRabbit
Late-Ack RabbitMQ integration for Akka Streams.  Not this is not full-featured at all!  It basically facilitates a reactive Akka stream for RabbitMQ for direct queue publish/consume and topic-based publish/consume.  Pub/Sub and other behaviors are not supported.

LateRabbit is inspired by op-rabbit, where the RabbitControl concept came from, and from reative-rabbit where the QMessage concept came from.

op-rabbit is super cool, and in the long haul will likely be the right/better solution, but I was concerned about the approach of wrapping all the Akka Stream primitives in "acked" versions of themselves.  The Akka Streams library is still a very young 1.0, meaning it will certainly evolve and change quite a bit.  I think if Streams were mature/stable the op-rabbit approach would be fantastic and transparent.  I do really like op-rabbit's use of Actor-based queue management.

So I've opted to wrap my stream messages in a wrapper containing the delivery tag for ack-ing, like reactive-rabbit does.  It's less elegant but I believe it will better endure Streams' evolution for the time being.

This project seeks a minimal "fusion" of the two concepts.
