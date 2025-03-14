package shared.utils.list;

public class LinkedList<V> implements ILinkedList<V> {

    // Internal Class Node

    // SinglyLinkedList Attributes
    private Node head;
    private Node tail;
    private int size;

    public class Node {
        V data;
        Node next;
        Node prev;

        // Private constructor
        Node(V data) {
            this.data = data;
            this.next = null;
            this.prev = null;
        }

        public V getData() {
            return data;
        }

        public Node getNext() {
            return next;
        }

        public Node getPrev() {
            return prev;
        }

    }

    public LinkedList() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    // SinglyLinkedList Methods for Node Types
    private Node searchNode(V crit) {
        Node t = head;
        while (t != null) {
            if (t.data.equals(crit)) {
                return t;
            }
            t = t.next;
        }
        return null;

    }

    public void addFirst(V value) {

        Node n = new Node(value);

        if (size == 0) {
            head = n;
            tail = n;
        } else {
            n.next = head;
            head.prev = n;
            head = n;
        }
        size++;
    }

    public void addLast(V value) {

        Node n = new Node(value);

        if (size == 0) {
            head = n;
            tail = n;
        } else {
            tail.next = n;
            n.prev = tail;
            tail = n;
        }
        size++;
    }

    public boolean addAfter(V value, V crit) {
        Node t = searchNode(crit);

        if (t == null) {
            // System.out.println("Element not found");
            return false;
        } else {
            Node n = new Node(value);
            if (n.next != null) {
                tail = n;
            }
            n.next = t.next;
            n.prev = t;
            t.next = n;

            Node frente = n.next;
            if (frente != null) {
                frente.prev = n;
            }
            size++;
            return true;
        }
    }

    public V removeFirst() {
        Node t = head;
        V rData = null;

        if (head == null) {
            // System.out.println("Empty list");
            return null;
        }
        if (head == tail) {
            rData = head.data;
            head = null;
            tail = null;
            // System.out.println("last element removed from list");
        } else {
            head = head.next;
            head.prev = null;
        }

        t.next = null;

        size--;
        // System.out.println("First element removed from list");

        return rData;
    }

    public V removeLast() {
        V rData = null;

        if (tail == null) {
            // System.out.println("Empty list");
            return null;
        } else {
            rData = tail.data;
            if (head == tail) {
                head = null;
                tail = null;
                // System.out.println("last element removed from list");
            } else {
                // System.out.println("element removed from list");
                Node tBefore = tail.prev;
                tail.prev = null;
                tail = tBefore;
                tail.next = null;
            }

            size--;
            // System.out.println("Last element removed from list");

        }
        return rData;
    }

    public V removeCrit(V crit) {
        V rData = null;

        if (isEmpty()) {
            // ystem.out.println("Empty list");
            return null;
        }

        Node tBefore = null;
        Node removed = searchNode(crit);

        if (removed == null) {
            // System.out.println("Element not found");
            return null;
        }

        tBefore = removed.prev; // safe from null pointer

        if (tBefore == null) {
            return removeFirst();
        } else if (removed == tail) {
            return removeLast();
        } else {
            Node front = removed.next;

            tBefore.next = front;
            if (front != null) {
                front.prev = tBefore;
            }

            removed.next = null;
            removed.prev = null;

            size--;

            rData = removed.data;
            return rData;
        }
    }

    public V peekFirst() {
        // peekFirst
        if (head == null) {
            // System.out.println("Empty list");
            return null;
        }
        return head.data;

    }

    public V peekLast() {
        // peekLast
        if (head == null) {
            // System.out.println("Empty list");
            return null;
        }
        return tail.data;
    }

    public V search(V crit) {
        Node t = searchNode(crit);
        if (t == null) {
            return null;
        }
        return t.data;
    }

    public boolean isEmpty() {
        if (head != null) {
            return false;
        }

        return true;
    }

    public void show() {
        Node t = head;

        if (isEmpty()) {
            // System.out.println("Empty list");
            return;
        }
        while (t != null) {
            System.out.println(t.data);
            t = t.next;
        }
    }

    public void showReverse() {
        Node t = tail;

        if (isEmpty()) {
            // System.out.println("Empty list");
            return;
        }
        while (t != null) {
            System.out.println(t.data);
            t = t.prev;
        }
    }

    public int getSize() {
        return size;
    }

    public Node getHead() {
        return head;
    }
}