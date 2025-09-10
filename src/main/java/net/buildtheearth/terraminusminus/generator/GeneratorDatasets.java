package net.buildtheearth.terraminusminus.generator;

import lombok.Getter;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.util.CustomAttributeContainer;

import java.util.Map;

/**
 * Wrapper class which contains all of the datasets used by {@link EarthGenerator}.
 *
 * @author DaPorkchop_
 */
@Getter
public class GeneratorDatasets extends CustomAttributeContainer {
    protected final GeographicProjection projection;

    public GeneratorDatasets(@NonNull EarthGeneratorSettings settings) {
        super(EarthGeneratorPipelines.datasets(settings));

        this.projection = settings.projection();
    }

    public GeneratorDatasets(@NonNull Map<String, Object> datasets, @NonNull GeographicProjection projection) {
        super(datasets);
        this.projection = projection;
    }
}
