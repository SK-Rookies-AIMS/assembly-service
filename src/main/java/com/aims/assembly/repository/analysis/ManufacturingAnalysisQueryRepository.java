package com.aims.assembly.repository.analysis;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.assembly.QAssemblyAnalysisResult;
import com.aims.assembly.domain.body.QBodyAnalysisResult;
import com.aims.assembly.domain.paint.QPaintAnalysisResult;
import com.aims.assembly.domain.press.QPressAnalysisResult;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ManufacturingAnalysisQueryRepository {

    private final JPAQueryFactory queryFactory;

    private final com.aims.assembly.domain.analysis.QManufacturingAnalysisResult manufacturingAnalysisResult =
            new com.aims.assembly.domain.analysis.QManufacturingAnalysisResult("manufacturingAnalysisResult");
    private final QPressAnalysisResult press = new QPressAnalysisResult("pressAnalysisResult");
    private final QBodyAnalysisResult body = new QBodyAnalysisResult("bodyAnalysisResult");
    private final QPaintAnalysisResult paint = new QPaintAnalysisResult("paintAnalysisResult");
    private final QAssemblyAnalysisResult assembly = new QAssemblyAnalysisResult("assemblyAnalysisResult");

    public ManufacturingAnalysisQueryRepository(
            @Qualifier("jpaQueryFactory") JPAQueryFactory queryFactory
    ) {
        this.queryFactory = queryFactory;
    }

    public Optional<ManufacturingAnalysisResult> findLatestByEventId(String eventId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(manufacturingAnalysisResult)
                        .where(manufacturingAnalysisResult.eventId.eq(eventId))
                        .orderBy(
                                manufacturingAnalysisResult.analyzedAt.desc(),
                                manufacturingAnalysisResult.createdAt.desc()
                        )
                        .fetchFirst()
        );
    }

    public List<ManufacturingAnalysisResult> findRecent(int limit) {
        return queryFactory.selectFrom(manufacturingAnalysisResult)
                .orderBy(
                        manufacturingAnalysisResult.analyzedAt.desc(),
                        manufacturingAnalysisResult.createdAt.desc()
                )
                .limit(limit)
                .fetch();
    }

    public List<ManufacturingAnalysisResult> findByCarMasterId(long carMasterId) {
        return queryFactory.selectFrom(manufacturingAnalysisResult)
                .where(manufacturingAnalysisResult.carMasterId.eq(carMasterId))
                .orderBy(
                        manufacturingAnalysisResult.eventTime.asc(),
                        manufacturingAnalysisResult.analyzedAt.asc()
                )
                .fetch();
    }

    public boolean hasPressDetail(String analysisId) {
        return exists(
                queryFactory.selectOne()
                        .from(press)
                        .where(press.analysisResult.analysisId.eq(analysisId))
        );
    }

    public boolean hasBodyDetail(String analysisId) {
        return exists(
                queryFactory.selectOne()
                        .from(body)
                        .where(body.analysisResult.analysisId.eq(analysisId))
        );
    }

    public boolean hasPaintDetail(String analysisId) {
        return exists(
                queryFactory.selectOne()
                        .from(paint)
                        .where(paint.analysisResult.analysisId.eq(analysisId))
        );
    }

    public boolean hasAssemblyDetail(String analysisId) {
        return exists(
                queryFactory.selectOne()
                        .from(assembly)
                        .where(assembly.analysisResult.analysisId.eq(analysisId))
        );
    }

    private boolean exists(com.querydsl.jpa.impl.JPAQuery<?> query) {
        return query.fetchFirst() != null;
    }
}
