using HealthAggregator.Data.Entities;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Data;

public sealed class HealthAggregatorDbContext(DbContextOptions<HealthAggregatorDbContext> options) : DbContext(options)
{
    public DbSet<EpicConnection> EpicConnections => Set<EpicConnection>();
    public DbSet<EpicAuthorizationState> EpicAuthorizationStates => Set<EpicAuthorizationState>();
    public DbSet<SyncJob> SyncJobs => Set<SyncJob>();
    public DbSet<SourceRecord> SourceRecords => Set<SourceRecord>();
    public DbSet<PatientRecord> Patients => Set<PatientRecord>();
    public DbSet<LabObservation> LabObservations => Set<LabObservation>();
    public DbSet<DiagnosticReportRecord> DiagnosticReports => Set<DiagnosticReportRecord>();
    public DbSet<ConditionRecord> Conditions => Set<ConditionRecord>();
    public DbSet<MedicationRecord> Medications => Set<MedicationRecord>();
    public DbSet<AllergyRecord> Allergies => Set<AllergyRecord>();
    public DbSet<EncounterRecord> Encounters => Set<EncounterRecord>();
    public DbSet<DocumentRecord> Documents => Set<DocumentRecord>();
    public DbSet<AiThread> AiThreads => Set<AiThread>();
    public DbSet<AiMessage> AiMessages => Set<AiMessage>();
    public DbSet<AuditLog> AuditLog => Set<AuditLog>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<EpicConnection>()
            .HasIndex(connection => connection.OrganizationId)
            .IsUnique();

        modelBuilder.Entity<EpicAuthorizationState>()
            .HasIndex(state => state.State)
            .IsUnique();

        modelBuilder.Entity<SourceRecord>()
            .HasIndex(record => new { record.SourceSystem, record.ResourceType, record.ResourceId })
            .IsUnique();

        modelBuilder.Entity<PatientRecord>()
            .HasIndex(patient => new { patient.SourceSystem, patient.FhirId })
            .IsUnique();

        modelBuilder.Entity<LabObservation>()
            .HasIndex(observation => new { observation.SourceSystem, observation.FhirReference })
            .IsUnique();
        modelBuilder.Entity<LabObservation>()
            .HasIndex(observation => new { observation.LoincCode, observation.EffectiveAt });
        modelBuilder.Entity<LabObservation>()
            .Property(observation => observation.NumericValue)
            .HasPrecision(18, 6);
        modelBuilder.Entity<LabObservation>()
            .Property(observation => observation.ReferenceLow)
            .HasPrecision(18, 6);
        modelBuilder.Entity<LabObservation>()
            .Property(observation => observation.ReferenceHigh)
            .HasPrecision(18, 6);

        modelBuilder.Entity<DiagnosticReportRecord>()
            .HasIndex(report => new { report.SourceSystem, report.FhirReference })
            .IsUnique();

        modelBuilder.Entity<ConditionRecord>()
            .HasIndex(condition => new { condition.SourceSystem, condition.FhirReference })
            .IsUnique();

        modelBuilder.Entity<MedicationRecord>()
            .HasIndex(medication => new { medication.SourceSystem, medication.FhirReference })
            .IsUnique();

        modelBuilder.Entity<AllergyRecord>()
            .HasIndex(allergy => new { allergy.SourceSystem, allergy.FhirReference })
            .IsUnique();

        modelBuilder.Entity<EncounterRecord>()
            .HasIndex(encounter => new { encounter.SourceSystem, encounter.FhirReference })
            .IsUnique();

        modelBuilder.Entity<DocumentRecord>()
            .HasIndex(document => new { document.SourceSystem, document.FhirReference })
            .IsUnique();
    }
}
