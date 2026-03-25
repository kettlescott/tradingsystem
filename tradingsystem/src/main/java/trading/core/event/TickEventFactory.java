package core.event;

import com.lmax.disruptor.EventFactory;

public class TickEventFactory implements EventFactory<TickEvent> {
    @Override
    public TickEvent newInstance() {
        return new TickEvent();
    }
}
