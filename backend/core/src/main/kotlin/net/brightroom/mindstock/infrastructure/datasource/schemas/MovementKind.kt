package net.brightroom.mindstock.infrastructure.datasource.schemas

/** stock_movements の sealed StockMovement 判別子(永続専用・ドメインではない)。 */
enum class MovementKind { REPLENISHMENT, CONSUMPTION, CORRECTION }
