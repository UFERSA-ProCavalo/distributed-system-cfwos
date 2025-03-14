package shared.utils.list;

public interface ILinkedList<V> {
    // criterio - valor
    void addFirst(V value);

    void addLast(V value);

    boolean addAfter(V value, V crit);

    V removeFirst();

    V removeLast();

    V removeCrit(V crit);

    V peekFirst();

    V peekLast();

    V search(V crit);

    boolean isEmpty();

    void show();

    void showReverse();

    int getSize();
}