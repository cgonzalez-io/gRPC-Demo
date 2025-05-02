package example.services;

import io.grpc.stub.StreamObserver;
import service.SortGrpc;
import service.SortRequest;
import service.SortResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SortImpl extends SortGrpc.SortImplBase {

    @Override
    public void sort(SortRequest request, StreamObserver<SortResponse> responseObserver) {
        List<Integer> input = request.getDataList();
        List<Integer> output;

        try {
            switch (request.getAlgo()) {
                case MERGE:
                    output = mergeSort(input);
                    break;
                case QUICK:
                    output = quickSort(input);
                    break;
                case INTERN:
                    // Use Java's built-in sort (TimSort for List)
                    output = new ArrayList<>(input);
                    Collections.sort(output);
                    break;
                default:
                    responseObserver.onNext(SortResponse.newBuilder()
                            .setIsSuccess(false)
                            .setError("Unknown sorting algorithm: " + request.getAlgo())
                            .build());
                    responseObserver.onCompleted();
                    return;
            }

            // Build successful response
            SortResponse response = SortResponse.newBuilder()
                    .setIsSuccess(true)
                    .addAllData(output)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            // In case of unexpected error
            SortResponse errorResp = SortResponse.newBuilder()
                    .setIsSuccess(false)
                    .setError("Sorting failed: " + e.getMessage())
                    .build();
            responseObserver.onNext(errorResp);
            responseObserver.onCompleted();
        }
    }

    // Merge sort implementation
    private List<Integer> mergeSort(List<Integer> list) {
        if (list.size() <= 1) {
            return new ArrayList<>(list);
        }
        int mid = list.size() / 2;
        List<Integer> left = mergeSort(list.subList(0, mid));
        List<Integer> right = mergeSort(list.subList(mid, list.size()));
        return merge(left, right);
    }

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

    // Quick sort implementation using a functional-style pivot partition
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
