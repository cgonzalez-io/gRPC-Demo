package example.services;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.SortGrpc;
import service.SortRequest;
import service.SortResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * gRPC implementation of the Sort service with robust error handling.
 */
public class SortImpl extends SortGrpc.SortImplBase {
    private static final Logger logger = LoggerFactory.getLogger(SortImpl.class);

    /**
     * Handles the sorting of a list of integers based on the specified algorithm.
     *
     * @param request          the sorting request containing the list of integers and the sorting algorithm to be applied
     * @param responseObserver the observer used to send the response or error back to the client
     */
    @Override
    public void sort(SortRequest request, StreamObserver<SortResponse> responseObserver) {
        if (request == null) {
            responseObserver.onError(
                    new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Request cannot be null"))
            );
            return;
        }

        List<Integer> input = request.getDataList();
        if (input == null) {
            responseObserver.onError(
                    new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Data list cannot be null"))
            );
            return;
        }

        // Empty list is valid: return success with empty data
        if (input.isEmpty()) {
            responseObserver.onNext(
                    SortResponse.newBuilder()
                            .setIsSuccess(true)
                            .addAllData(Collections.emptyList())
                            .build()
            );
            responseObserver.onCompleted();
            return;
        }

        try {
            List<Integer> output;
            switch (request.getAlgo()) {
                case MERGE:
                    output = mergeSort(input);
                    break;
                case QUICK:
                    output = quickSort(input);
                    break;
                case INTERN:
                    output = new ArrayList<>(input);
                    Collections.sort(output);
                    break;
                default:
                    // Unknown enum value: invalid argument
                    responseObserver.onError(
                            new StatusRuntimeException(
                                    Status.INVALID_ARGUMENT
                                            .withDescription("Unknown sorting algorithm: " + request.getAlgo())
                            )
                    );
                    return;
            }

            // Build and send successful response
            responseObserver.onNext(
                    SortResponse.newBuilder()
                            .setIsSuccess(true)
                            .addAllData(output)
                            .build()
            );
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            // Unexpected server error
            responseObserver.onError(
                    new StatusRuntimeException(
                            Status.INTERNAL
                                    .withDescription("Sorting failed: " + e.getMessage())
                    )
            );
        }
    }

    /**
     * Performs the merge sort algorithm on the given list of integers.
     * The method recursively splits the list into smaller sublists,
     * sorts them, and merges the sorted sublists back together.
     *
     * @param list the list of integers to be sorted
     * @return a new sorted list of integers in ascending order
     */
    private List<Integer> mergeSort(List<Integer> list) {
        if (list.size() <= 1) {
            return new ArrayList<>(list);
        }
        int mid = list.size() / 2;
        List<Integer> left = mergeSort(list.subList(0, mid));
        List<Integer> right = mergeSort(list.subList(mid, list.size()));
        return merge(left, right);
    }

    /**
     * Merges two sorted lists into a single sorted list.
     *
     * @param left  the first sorted list
     * @param right the second sorted list
     * @return a new list containing all elements from both input lists in sorted order
     */
    private List<Integer> merge(List<Integer> left, List<Integer> right) {
        List<Integer> merged = new ArrayList<>(left.size() + right.size());
        int i = 0, j = 0;
        while (i < left.size() && j < right.size()) {
            if (left.get(i) <= right.get(j)) {
                merged.add(left.get(i++));
            } else {
                merged.add(right.get(j++));
            }
        }
        while (i < left.size()) merged.add(left.get(i++));
        while (j < right.size()) merged.add(right.get(j++));
        return merged;
    }

    /**
     * Sorts the given list of integers using the QuickSort algorithm.
     * This method is a recursive implementation that partitions the list
     * into sublists around a pivot element and sorts each sublist independently.
     *
     * @param list the list of integers to be sorted
     *             The input list is not modified, and the method operates
     *             on a copy of the list.
     * @return a new list of integers sorted in ascending order
     */
    private List<Integer> quickSort(List<Integer> list) {
        if (list.size() <= 1) {
            return new ArrayList<>(list);
        }
        int pivot = list.get(list.size() / 2);
        List<Integer> less = new ArrayList<>();
        List<Integer> equal = new ArrayList<>();
        List<Integer> greater = new ArrayList<>();

        for (int num : list) {
            if (num < pivot) less.add(num);
            else if (num == pivot) equal.add(num);
            else greater.add(num);
        }
        List<Integer> sorted = new ArrayList<>();
        sorted.addAll(quickSort(less));
        sorted.addAll(equal);
        sorted.addAll(quickSort(greater));
        return sorted;
    }
}
