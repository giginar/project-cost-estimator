export interface GanttTask {
  id: string;
  code: string;
  name: string;
  wbs: string;
  start: string;
  end: string;
  assignments: ActivityAssignment[];
}

export interface ActivityAssignment { id: string; resourceId: string; resourceName: string; resourceType: string; quantity: number; plannedWork: number; workUnit: string | null; }
export interface ResourceCost { id: string; category: string; name: string; calculationBasis: string; unitPrice: number; unit: string | null; }
export interface FuelConsumption { id: string; fuelType: string; consumptionPerHour: number; consumptionUnit: string; }

export interface ActivityResource {
  id: string;
  type: ResourceType;
  code: string;
  name: string;
  description?: string;
  subtype: string;
  roleName?: string; skillLevel?: string; genericResource?: boolean;
  manufacturer?: string; model?: string; capacity?: number; capacityUnit?: string; owned?: boolean;
  defaultUnit?: string;
  costs: ResourceCost[];
  fuelConsumptions: FuelConsumption[];
}

export type ResourceType = 'personnel' | 'equipment' | 'material';
export interface NewResource {
  type: ResourceType; code: string; name: string; description: string; subtype: string; defaultUnit: string;
  unitPrice: number | null; calculationBasis: string; fuelType: string; fuelConsumption: number | null; fuelUnitPrice: number | null;
}

export interface ResourceSelection { task: GanttTask; addResourceIds: string[]; removeAssignmentIds: string[]; }
