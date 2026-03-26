package site.arookieofc.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessOperation {
    String action();
    String targetType() default "";
    String targetIdParam() default "";
    String targetNameParam() default "";
    String detail() default "";
}
