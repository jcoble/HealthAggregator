package com.healthaggregator

object TestFhirFixtures {
	const val LAB_HBA1C = """
        {
          "resourceType": "Observation",
          "id": "hba1c-1",
          "status": "final",
          "category": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/observation-category", "code": "laboratory"}]}],
          "code": {"coding": [{"system": "http://loinc.org", "code": "4548-4", "display": "HbA1c"}], "text": "Hemoglobin A1c"},
          "subject": {"reference": "Patient/p1"},
          "effectiveDateTime": "2024-03-15T09:30:00Z",
          "valueQuantity": {"value": 5.8, "unit": "%"},
          "referenceRange": [{"low": {"value": 4.0}, "high": {"value": 5.6}}],
          "interpretation": [{"coding": [{"code": "H"}]}]
        }
    """

	const val VITAL_BP = """
        {
          "resourceType": "Observation",
          "id": "bp-1",
          "status": "final",
          "category": [{"coding": [{"code": "vital-signs"}]}],
          "code": {"coding": [{"system": "http://loinc.org", "code": "85354-9"}], "text": "Blood Pressure"},
          "subject": {"reference": "Patient/p1"},
          "effectiveDateTime": "2024-03-15T09:30:00Z",
          "component": [
            {"code": {"coding": [{"system": "http://loinc.org", "code": "8480-6"}], "text": "Systolic"}, "valueQuantity": {"value": 120, "unit": "mmHg"}},
            {"code": {"coding": [{"system": "http://loinc.org", "code": "8462-4"}], "text": "Diastolic"}, "valueQuantity": {"value": 80, "unit": "mmHg"}}
          ]
        }
    """

	const val CONDITION_ASTHMA = """
        {
          "resourceType": "Condition",
          "id": "asthma-1",
          "code": {"text": "Asthma"},
          "clinicalStatus": {"coding": [{"code": "active"}]},
          "onsetDateTime": "2015-06-01",
          "subject": {"reference": "Patient/p1"}
        }
    """

	const val UNKNOWN_RESOURCE = """
        {
          "resourceType": "Goal",
          "id": "unknown-1",
          "description": {"text": "Exercise more"}
        }
    """
}
