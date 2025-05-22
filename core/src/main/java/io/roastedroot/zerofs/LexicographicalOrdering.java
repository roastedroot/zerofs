// FROM: com.google.common.collect
package io.roastedroot.zerofs;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Iterator;

final class LexicographicalOrdering<T extends Object>
        implements Comparator<Iterable<T>>, Serializable {
    final Comparator<? super T> elementOrder;

    LexicographicalOrdering(Comparator<? super T> elementOrder) {
        this.elementOrder = elementOrder;
    }

    @Override
    public int compare(Iterable<T> leftIterable, Iterable<T> rightIterable) {
        Iterator<T> left = leftIterable.iterator();
        Iterator<T> right = rightIterable.iterator();
        while (left.hasNext()) {
            if (!right.hasNext()) {
                return 1; // because it's longer
            }
            int result = elementOrder.compare(left.next(), right.next());
            if (result != 0) {
                return result;
            }
        }
        if (right.hasNext()) {
            return -1; // because it's longer
        }
        return 0;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof LexicographicalOrdering) {
            LexicographicalOrdering<?> that = (LexicographicalOrdering<?>) object;
            return this.elementOrder.equals(that.elementOrder);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return elementOrder.hashCode() ^ 2075626741; // meaningless
    }

    @Override
    public String toString() {
        return elementOrder + ".lexicographical()";
    }
}
