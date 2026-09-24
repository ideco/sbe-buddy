package corpus.unions;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.SbeUnion;

/**
 * The orders, a union of its own inside {@link Ingress}, its messages nested.
 */
@SbeUnion
public sealed interface OrderCommand extends Ingress permits OrderCommand.PlaceOrder, OrderCommand.CancelOrder {

	/** The order id every order command carries, which the codecs ignore. */
	long orderId();

	@SbeMessage(id = 1)
	record PlaceOrder(
			@SbeField(id = 1) long orderId,
			@SbeField(id = 2) int quantity
	) implements OrderCommand, Audited {
	}

	@SbeMessage(id = 2)
	record CancelOrder(@SbeField(id = 1) long orderId) implements OrderCommand {
	}
}
