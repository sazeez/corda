package sandbox.java.util.function;

/**
 * This is a dummy class that implements just enough of [java.util.function.Function]
 * to allow us to compile [sandbox.Task].
 */
@FunctionalInterface
public interface Function<T, R> {
    R apply(T item);
}
