package io.swerr.pocketbrowser.ui.widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.network.chat.Component;

public final class OptionButton<T> extends ThemedButton {
    private final List<T> values;
    private final Function<T, Component> naming;
    private final Consumer<T> onChange;
    private int index;

    public OptionButton(List<T> values, T initial, Function<T, Component> naming, Consumer<T> onChange) {
        super(0, 0, 0, 0, Component.empty(), null);
        this.values = values;
        this.naming = naming;
        this.onChange = onChange;
        this.index = Math.max(0, values.indexOf(initial));
        refresh();
    }

    public T value() {
        return values.get(index);
    }

    private void refresh() {
        setMessage(naming.apply(value()));
    }

    private void step(int direction) {
        if (values.size() < 2) {
            return;
        }
        index = Math.floorMod(index + direction, values.size());
        refresh();
        onChange.accept(value());
    }

    @Override
    protected void press() {
        step(1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            step(scrollY > 0.0D ? 1 : -1);
            return true;
        }
        return false;
    }
}
