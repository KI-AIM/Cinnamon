package de.kiaim.cinnamon.platform.model.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import de.kiaim.cinnamon.platform.model.serialization.TrimmingStringDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.lang.annotation.*;

@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonDeserialize(using = TrimmingStringDeserializer.class)
public @interface Trim {
}
