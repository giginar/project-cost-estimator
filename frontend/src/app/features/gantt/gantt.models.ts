export interface GanttTask {
  id: string;
  code: string;
  name: string;
  wbs: string;
  start: string;
  end: string;
  assignments: ActivityAssignment[];
  plannedQuantity?: number | null;
  quantityUnit?: string | null;
  dailyProductionRate?: number | null;
  autoSchedule?: boolean;
  dependencies?: ActivityDependency[];
}
export interface ActivityDependency { id: string; predecessorActivityId: string; predecessorCode: string; predecessorName: string; type: string; lagDays: number; }

export interface ActivityAssignment {
  id: string; resourceId: string; resourceName: string; resourceType: string; quantity: number; plannedWork: number; workUnit: string | null;
  utilizationRate: number; startDate: string; endDate: string; overtimeAllowed: boolean; personnelAssignmentType: string | null;
  operatingHoursPerDay: number | null; standbyHoursPerDay: number | null; requiredQuantity: number | null; wastePercentage: number | null;
}
export interface ResourceCost { id: string; category: string; name: string; calculationBasis: string; unitPrice: number; unit: string | null; currencyCode: string; taxable?: boolean; taxRate?: number | null; validFrom?: string | null; validTo?: string | null; generated?: boolean; }
export interface FuelConsumption { id: string; fuelType: string; consumptionPerHour: number; standbyConsumptionPerHour: number | null; consumptionUnit: string; }

export interface ActivityResource {
  id: string;
  type: ResourceType;
  code: string;
  name: string;
  description?: string;
  shared?: boolean;
  ownerProjectId?: string | null;
  assignable?: boolean;
  subtype: string;
  roleName?: string; skillLevel?: string; genericResource?: boolean;
  manufacturer?: string; model?: string; capacity?: number; capacityUnit?: string; owned?: boolean;
  defaultUnit?: string;
  equipmentEconomics?: EquipmentEconomics | null;
  materialProcurement?: MaterialProcurement | null;
  costs: ResourceCost[];
  fuelConsumptions: FuelConsumption[];
}
export interface EquipmentEconomics { owned: boolean; acquisitionCost: number | null; residualValue: number | null; usefulLifeMonths: number | null; maintenanceRatePercentage: number | null; insuranceRatePercentage: number | null; currencyCode: string; monthlyDepreciation: number; monthlyMaintenance: number; monthlyInsurance: number; }
export interface MaterialProcurement { supplier: string | null; leadTimeDays: number | null; minimumOrderQuantity: number | null; defaultWastePercentage: number | null; }

export type ResourceType = 'personnel' | 'equipment' | 'material';
export interface NewResource {
  type: ResourceType; code: string; name: string; description: string; subtype: string; defaultUnit: string;
  shared: boolean;
  unitPrice: number | null; calculationBasis: string; fuelType: string; fuelConsumption: number | null; standbyFuelConsumption: number | null; fuelUnitPrice: number | null;
}

export interface AssignmentDraft {
  id: string | null; resourceId: string; quantity: number; plannedWork: number; workUnit: string | null; utilizationRate: number;
  startDate: string; endDate: string; overtimeAllowed: boolean; personnelAssignmentType: string | null;
  operatingHoursPerDay: number; standbyHoursPerDay: number; requiredQuantity: number; wastePercentage: number;
}
export interface ResourceSelection { task: GanttTask; assignments: AssignmentDraft[]; removeAssignmentIds: string[]; }
