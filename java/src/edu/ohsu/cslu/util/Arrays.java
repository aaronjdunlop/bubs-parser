package edu.ohsu.cslu.util;

public class Arrays {

    public static void insertGaps(final Object[] oldArray, final int[] gapIndices, final Object[] newArray,
            final Object gap) {
        int currentGap = 0;
        int oldJ = 0;
        for (int newJ = 0; newJ < newArray.length; newJ++) {
            if (currentGap < gapIndices.length && oldJ == gapIndices[currentGap]) {
                newArray[newJ] = gap;
                currentGap++;
            } else {
                newArray[newJ] = oldArray[oldJ++];
            }
        }
    }

    public static void insertGaps(final Object[] oldArray, final int[] gapIndices, final Object[] newArray,
            final Object[] gaps) {
        int currentGap = 0;
        int oldJ = 0;
        for (int newJ = 0; newJ < newArray.length; newJ++) {
            if (currentGap < gapIndices.length && oldJ == gapIndices[currentGap]) {
                newArray[newJ] = gaps[currentGap];
                currentGap++;
            } else {
                newArray[newJ] = oldArray[oldJ++];
            }
        }
    }

    /**
     * Sort parallel int[] arrays (stolen from java.util.Arrays and modified to sort parallel arrays)
     */
    public static void sort(final int k[], final int v[]) {
        sort1(k, v, 0, k.length);
    }

    private static void sort1(final int x[], final int v[], final int off, final int len) {
        // Insertion sort on smallest arrays
        if (len < 7) {
            for (int i = off; i < len + off; i++)
                for (int j = i; j > off && x[j - 1] > x[j]; j--)
                    swap(x, v, j, j - 1);
            return;
        }

        // Choose a partition element, v
        int m = off + (len >> 1); // Small arrays, middle element
        if (len > 7) {
            int l = off;
            int n = off + len - 1;
            if (len > 40) { // Big arrays, pseudomedian of 9
                final int s = len / 8;
                l = med3(x, l, l + s, l + 2 * s);
                m = med3(x, m - s, m, m + s);
                n = med3(x, n - 2 * s, n - s, n);
            }
            m = med3(x, l, m, n); // Mid-size, med of 3
        }
        final int val = x[m];

        // Establish Invariant: v* (<v)* (>v)* v*
        int a = off, b = a, c = off + len - 1, d = c;
        while (true) {
            while (b <= c && x[b] <= val) {
                if (x[b] == val)
                    swap(x, v, a++, b);
                b++;
            }
            while (c >= b && x[c] >= val) {
                if (x[c] == val)
                    swap(x, v, c, d--);
                c--;
            }
            if (b > c)
                break;
            swap(x, v, b++, c--);
        }

        // Swap partition elements back to middle
        int s;
        final int n = off + len;
        s = java.lang.Math.min(a - off, b - a);
        vecswap(x, v, off, b - s, s);
        s = java.lang.Math.min(d - c, n - d - 1);
        vecswap(x, v, b, n - s, s);

        // Recursively sort non-partition-elements
        if ((s = b - a) > 1)
            sort1(x, v, off, s);
        if ((s = d - c) > 1)
            sort1(x, v, n - s, s);
    }

    /**
     * Swaps x[a] with x[b] and f[a] with f[b].
     */
    private static void swap(final int k[], final int v[], final int a, final int b) {
        final int t = k[a];
        k[a] = k[b];
        k[b] = t;

        final int t2 = v[a];
        v[a] = v[b];
        v[b] = t2;
    }

    /**
     * Swaps k[a .. (a+n-1)] with k[b .. (b+n-1)] and v[a .. (a+n-1)] with v[b .. (b+n-1)].
     */
    private static void vecswap(final int k[], final int v[], int a, int b, final int n) {
        for (int i = 0; i < n; i++, a++, b++) {
            swap(k, v, a, b);
        }
    }

    /**
     * Returns the index of the median of the three indexed integers.
     */
    private static int med3(final int x[], final int a, final int b, final int c) {
        return (x[a] < x[b] ? (x[b] < x[c] ? b : x[a] < x[c] ? c : a) : (x[b] > x[c] ? b : x[a] > x[c] ? c
                : a));
    }

    /**
     * Sort parallel int[] and float[] arrays (stolen from java.util.Arrays and modified to sort parallel
     * arrays)
     * 
     * TODO Untested (caveat emptor)
     */
    public static void sort(final int x[], final float f[]) {
        sort1(x, f, 0, x.length);
    }

    private static void sort1(final int x[], final float v[], final int off, final int len) {
        // Insertion sort on smallest arrays
        if (len < 7) {
            for (int i = off; i < len + off; i++)
                for (int j = i; j > off && x[j - 1] > x[j]; j--)
                    swap(x, v, j, j - 1);
            return;
        }

        // Choose a partition element, v
        int m = off + (len >> 1); // Small arrays, middle element
        if (len > 7) {
            int l = off;
            int n = off + len - 1;
            if (len > 40) { // Big arrays, pseudomedian of 9
                final int s = len / 8;
                l = med3(x, l, l + s, l + 2 * s);
                m = med3(x, m - s, m, m + s);
                n = med3(x, n - 2 * s, n - s, n);
            }
            m = med3(x, l, m, n); // Mid-size, med of 3
        }
        final int val = x[m];

        // Establish Invariant: v* (<v)* (>v)* v*
        int a = off, b = a, c = off + len - 1, d = c;
        while (true) {
            while (b <= c && x[b] <= val) {
                if (x[b] == val)
                    swap(x, v, a++, b);
                b++;
            }
            while (c >= b && x[c] >= val) {
                if (x[c] == val)
                    swap(x, v, c, d--);
                c--;
            }
            if (b > c)
                break;
            swap(x, v, b++, c--);
        }

        // Swap partition elements back to middle
        int s;
        final int n = off + len;
        s = java.lang.Math.min(a - off, b - a);
        vecswap(x, v, off, b - s, s);
        s = java.lang.Math.min(d - c, n - d - 1);
        vecswap(x, v, b, n - s, s);

        // Recursively sort non-partition-elements
        if ((s = b - a) > 1)
            sort1(x, v, off, s);
        if ((s = d - c) > 1)
            sort1(x, v, n - s, s);
    }

    /**
     * Swaps x[a] with x[b] and f[a] with f[b].
     */
    private static void swap(final int x[], final float f[], final int a, final int b) {
        final int t = x[a];
        x[a] = x[b];
        x[b] = t;

        final float t2 = f[a];
        f[a] = f[b];
        f[b] = t2;
    }

    /**
     * Swaps x[a .. (a+n-1)] with x[b .. (b+n-1)] and f[a .. (a+n-1)] with f[b .. (b+n-1)].
     */
    private static void vecswap(final int x[], final float f[], int a, int b, final int n) {
        for (int i = 0; i < n; i++, a++, b++) {
            swap(x, f, a, b);
        }
    }

    public static void reverse(final int[] array) {
        for (int i = 0, j = array.length - 1; i < array.length / 2; i++, j--) {
            final int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }
}
