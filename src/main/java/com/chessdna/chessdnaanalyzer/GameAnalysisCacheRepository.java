package com.chessdna.chessdnaanalyzer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameAnalysisCacheRepository extends JpaRepository<GameAnalysisCache, String> {}
