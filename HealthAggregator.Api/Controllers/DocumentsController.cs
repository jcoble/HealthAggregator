using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/documents")]
public sealed class DocumentsController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetDocuments(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Documents.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(d => d.TypeText != null && d.TypeText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(d => d.Status == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(d => d.SourceSystem == source);
        if (from.HasValue) query = query.Where(d => d.DocumentedAt >= from);
        if (to.HasValue)   query = query.Where(d => d.DocumentedAt <= to);

        var rows = (await query.ToListAsync(cancellationToken))
            .OrderByDescending(d => d.DocumentedAt)
            .Take(500)
            .ToList();

        return Ok(rows);
    }
}
