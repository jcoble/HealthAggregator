using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using HealthAggregator.Data;
using HealthAggregator.Data.Entities;
using HealthAggregator.Data.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/imports")]
public sealed class ImportsController(HealthAggregatorDbContext db, FhirImportService importer) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetImports(CancellationToken cancellationToken)
    {
        var imports = (await db.SyncJobs
            .AsNoTracking()
            .ToListAsync(cancellationToken))
            .OrderByDescending(job => job.StartedAt)
            .Take(100)
            .ToList();

        return Ok(imports);
    }

    [HttpPost]
    [RequestSizeLimit(50_000_000)]
    public async Task<IActionResult> Upload(
        [FromForm] IFormFile file,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        if (file.Length == 0)
        {
            return BadRequest("Upload a non-empty file.");
        }

        var sourceSystem = string.IsNullOrWhiteSpace(source) ? "manual-upload" : source;

        await using var stream = file.OpenReadStream();
        using var reader = new StreamReader(stream, Encoding.UTF8, detectEncodingFromByteOrderMarks: true);
        var content = await reader.ReadToEndAsync(cancellationToken);

        if (LooksLikeJson(content))
        {
            var summary = await importer.ImportBundleAsync(sourceSystem, file.FileName, content, cancellationToken);
            return Ok(new { kind = "fhir-json", source = sourceSystem, summary });
        }

        var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(content)));
        var record = await db.SourceRecords.SingleOrDefaultAsync(
            candidate => candidate.SourceSystem == sourceSystem
                && candidate.ResourceType == "UploadedFile"
                && candidate.ResourceId == hash,
            cancellationToken);

        if (record is null)
        {
            record = new SourceRecord
            {
                SourceSystem = sourceSystem,
                ResourceType = "UploadedFile",
                ResourceId = hash
            };
            db.SourceRecords.Add(record);
        }

        record.SourceName = file.FileName;
        record.FhirReference = $"UploadedFile/{hash}";
        record.RawJson = JsonSerializer.Serialize(new
        {
            file.FileName,
            file.ContentType,
            file.Length,
            Sha256 = hash,
            Note = "Stored as metadata only. Structured parsing for PDF/CSV/XLSX is a follow-up path."
        });
        record.ImportedAt = DateTimeOffset.UtcNow;
        db.AuditLog.Add(new AuditLog
        {
            Action = $"upload:{sourceSystem}",
            Detail = file.FileName,
            CreatedAt = DateTimeOffset.UtcNow
        });
        await db.SaveChangesAsync(cancellationToken);

        return Ok(new { kind = "stored-file-metadata", source = sourceSystem, hash });
    }

    private static bool LooksLikeJson(string content)
    {
        var trimmed = content.AsSpan().TrimStart();
        return trimmed.Length > 0 && trimmed[0] is '{' or '[';
    }
}
