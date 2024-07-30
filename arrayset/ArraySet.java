package info.kgeorgiy.ja.televnoi.arrayset;

import java.util.*;

@SuppressWarnings("unused")
public class ArraySet<E> extends AbstractSet<E> implements SortedSet<E> {
    private final Comparator<? super E> comparator;
    private final List<E> list;

    private ArraySet(Collection<? extends E> collection, Comparator<? super E> comparator, boolean mod) {
        this.comparator = comparator;
        if (mod) {
            Objects.requireNonNull(collection, "null collection");
            Set<E> set = new TreeSet<>(comparator);
            set.addAll(collection);
            this.list = List.copyOf(set);
        } else {
            this.list = List.of();
        }
    }

    public ArraySet() {
        this(null, null, false);
    }

    public ArraySet(Collection<? extends E> collection) {
        this(collection, null);
    }

    public ArraySet(Collection<? extends E> collection, Comparator<? super E> comparator) {
        this(collection, comparator, true);
    }

    public ArraySet(Comparator<? super E> comparator) {
        this(new ArrayList<>(), comparator);
    }

    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @SuppressWarnings("unchecked")
    private int compare(E o1, E o2, Comparator<? super E> comparator) {
        return comparator != null ? comparator.compare(o1, o2) : ((Comparable<E>) o1).compareTo(o2);
    }

    private int validSubIndex(E element) {
        int index = Collections.binarySearch(list, element, comparator);
        return index < 0 ? (-index - 1) : index;
    }

    private SortedSet<E> absSet(E fromElement, E toElement) {
        int l = fromElement == null ? 0 : validSubIndex(fromElement);
        int r = (toElement == null ? list.size() : validSubIndex(toElement)) - 1;
        return (!list.isEmpty() && -1 < l && l <= r && r < list.size()) ?
                new ArraySet<>(list.subList(l, r + 1), comparator) : new ArraySet<>(comparator);
    }

    private E nilCheck(E o) {
        return Objects.requireNonNull(o, "null element in params");
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        if (compare(fromElement, toElement, comparator) > 0) {
            throw new IllegalArgumentException("second element is lower than first");
        }
        return absSet(nilCheck(fromElement), nilCheck(toElement));
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return absSet(null, nilCheck(toElement));
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return absSet(nilCheck(fromElement), null);
    }

    private E getOnIndex(int index) {
        if (list.isEmpty()) {
            throw new NoSuchElementException("empty set");
        }
        return list.get(index);
    }

    @Override
    public E first() {
        return getOnIndex(0);
    }

    @Override
    public E last() {
        return getOnIndex(list.size() - 1);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return Collections.binarySearch(list, (E) o, comparator) >= 0;
    }
}
