package com.sinteticas.api.common.web;

import com.sinteticas.api.common.error.ApiException;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Utilidades para validar la paginación que llega del cliente.
 */
public final class Pageables {

    /** Tamaño máximo de página: evita que un cliente pida toda la tabla de una vez. */
    public static final int MAX_PAGE_SIZE = 100;

    private Pageables() {
    }

    /**
     * Rechaza ordenamientos por campos que no están en la lista blanca ({@code ?sort=...}).
     * Así un campo inválido devuelve 400 en vez de un error 500 desde la base de datos.
     */
    public static Pageable restrict(Pageable pageable, Set<String> allowedSortProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedSortProperties.contains(order.getProperty())) {
                throw ApiException.badRequest("INVALID_SORT",
                        "No se puede ordenar por '" + order.getProperty() + "'. Campos permitidos: "
                                + String.join(", ", allowedSortProperties.stream().sorted().toList()));
            }
        }
        return pageable;
    }

}
