package com.healthaggregator.data

import androidx.room.migration.Migration

/**
 * Migrations are defined in `app/src/main/assets/migrations/{N}_to_{M}.sql`.
 * MigrationFactory loads them at runtime. This file wires each pair to the factory.
 *
 * To add a new migration:
 *   1. Create `app/src/main/assets/migrations/{currentVersion}_to_{currentVersion+1}.sql`.
 *   2. Add a line to `allMigrations` below.
 *   3. Bump `@Database(version = ...)` in AppDatabase.kt.
 */
fun allMigrations(factory: MigrationFactory): Array<Migration> = arrayOf(
	factory.load(1, 2),
	factory.load(2, 3),
	factory.load(3, 4),
	factory.load(4, 5),
	factory.load(5, 6),
	factory.load(6, 7),
	factory.load(7, 8),
	factory.load(8, 9),
	factory.load(9, 10),
)
