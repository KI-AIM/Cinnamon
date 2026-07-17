package de.kiaim.cinnamon.platform.model.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.kiaim.cinnamon.platform.model.serialization.TrimmingStringDeserializer;

import java.lang.annotation.*;

@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonDeserialize(using = TrimmingStringDeserializer.class)
public @interface Trim {
}
