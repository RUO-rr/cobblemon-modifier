package com.cobblemon.modifier.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cobblemon 全局刷新配置的只读视图：稀有等级权重 + 生成位置权重。
 */
public record SpawnBucketConfig(
    Map<String, Float> positionTypeWeights,
    List<Bucket> buckets,
    boolean replaceWithNewVersion
) {

    public static final List<String> BUCKET_NAMES =
        List.of("common", "uncommon", "rare", "ultra-rare");

    public static final List<String> POSITION_TYPES =
        List.of("grounded", "submerged", "surface");

    public SpawnBucketConfig {
        Map<String, Float> weights = positionTypeWeights == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(positionTypeWeights);
        positionTypeWeights = Collections.unmodifiableMap(weights);
        buckets = buckets == null ? List.of() : List.copyOf(buckets);
    }

    public record Bucket(String name, float weight) {
        public Bucket {
            Objects.requireNonNull(name, "bucket name must not be null");
        }
    }

    public static SpawnBucketConfig defaults() {
        Map<String, Float> positions = new LinkedHashMap<>();
        positions.put("grounded", 1.0f);
        positions.put("submerged", 1.0f);
        positions.put("surface", 1.0f);
        return new SpawnBucketConfig(positions, List.of(
            new Bucket("common", 94.3f),
            new Bucket("uncommon", 5.0f),
            new Bucket("rare", 0.5f),
            new Bucket("ultra-rare", 0.2f)
        ), true);
    }

    public float weightOf(String bucketName) {
        for (Bucket bucket : buckets) {
            if (bucket.name().equals(bucketName)) {
                return bucket.weight();
            }
        }
        return 0.0f;
    }

    public float positionWeightOf(String positionType) {
        Float value = positionTypeWeights.get(positionType);
        return value == null ? 0.0f : value;
    }
}
