using HealthAggregator.Api.Configuration;
using HealthAggregator.Api.Services;
using HealthAggregator.Data;
using HealthAggregator.Data.Services;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddOpenApi();
builder.Services.Configure<EpicSettings>(builder.Configuration.GetSection(EpicSettings.SectionName));

var databasePath = builder.Configuration["Storage:DatabasePath"];
if (string.IsNullOrWhiteSpace(databasePath))
{
    databasePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "HealthAggregator",
        "healthaggregator.db");
}

var databaseDirectory = Path.GetDirectoryName(databasePath);
if (!string.IsNullOrWhiteSpace(databaseDirectory))
{
    Directory.CreateDirectory(databaseDirectory);
}

builder.Services.AddDbContext<HealthAggregatorDbContext>(options =>
    options.UseSqlite($"Data Source={databasePath}"));
builder.Services.AddScoped<FhirImportService>();
builder.Services.AddScoped<ReadOnlyAssistantService>();
builder.Services.AddHttpClient<SmartConfigurationClient>();
builder.Services.AddHttpClient<EpicFhirClient>();
builder.Services.AddCors(options =>
{
    options.AddPolicy("Frontend", policy =>
        policy.WithOrigins(
                "http://localhost:5373",
                "https://localhost:5373",
                "http://127.0.0.1:5373",
                "https://127.0.0.1:5373")
            .AllowAnyHeader()
            .AllowAnyMethod());
});

var app = builder.Build();

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<HealthAggregatorDbContext>();
    db.Database.Migrate();
}

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseCors("Frontend");

app.MapControllers();
app.MapGet("/", () => Results.Ok(new
{
    Name = "HealthAggregator API",
    Status = "ok",
    Database = databasePath
}));

app.Run();
