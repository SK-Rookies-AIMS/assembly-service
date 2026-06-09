package com.aims.assembly.utils;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.PathBuilder;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.Collectors;

public class QueryDSLUtil {

    // Pageable 객체의 Sort 조건을 QueryDSL의 OrderSpecifier 리스트로 변환
    public static <T> List<OrderSpecifier<?>> getOrderSpecifiers(Pageable pageable, Class<T> clazz, String alias) {
        PathBuilder<T> entityPath = new PathBuilder<>(clazz, alias);

        return pageable.getSort().stream()
                .map(order -> {
                    Order direction = order.isAscending() ? Order.ASC : Order.DESC;
                    // Comparable 타입으로 안전하게 변환
                    return new OrderSpecifier<>(
                            direction,
                            entityPath.getComparable(order.getProperty(), Comparable.class)
                    );
                })
                .collect(Collectors.toList());
    }
}