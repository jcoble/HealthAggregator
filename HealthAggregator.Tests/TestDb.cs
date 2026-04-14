using HealthAggregator.Data;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Tests;

public static class TestDb
{
    public static HealthAggregatorDbContext CreateInMemory()
    {
        var connection = new SqliteConnection("DataSource=:memory:");
        connection.Open();
        var options = new DbContextOptionsBuilder<HealthAggregatorDbContext>()
            .UseSqlite(connection)
            .Options;
        var db = new HealthAggregatorDbContext(options);
        db.Database.EnsureCreated();
        return db;
    }
}
