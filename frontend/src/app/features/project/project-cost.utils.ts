import { ActivityResource, GanttTask } from '../gantt/gantt.models';

export interface CostBreakdown { personnel: number; equipment: number; material: number; fuel: number; total: number; }

export function taskCosts(task: GanttTask, resources: ActivityResource[]): CostBreakdown {
  const result: CostBreakdown = { personnel: 0, equipment: 0, material: 0, fuel: 0, total: 0 };
  const days = daysInclusive(task.start, task.end);
  task.assignments.forEach(assignment => {
    const resource = resources.find(item => item.id === assignment.resourceId); if (!resource) return;
    resource.costs.forEach(cost => {
      if (cost.category === 'FUEL') return;
      const amount = cost.unitPrice * costFactor(cost.calculationBasis, days, assignment.quantity);
      result[resource.type] += amount;
    });
    if (resource.type === 'equipment') {
      const fuelPrice = resource.costs.find(cost => cost.category === 'FUEL');
      const consumption = resource.fuelConsumptions.reduce((sum, fuel) => sum + fuel.consumptionPerHour, 0);
      if (fuelPrice) result.fuel += fuelPrice.unitPrice * consumption * days * 8 * assignment.quantity;
    }
  });
  result.total = result.personnel + result.equipment + result.material + result.fuel;
  return result;
}

export function sumCosts(costs: CostBreakdown[]): CostBreakdown {
  return costs.reduce((sum, item) => ({ personnel: sum.personnel + item.personnel, equipment: sum.equipment + item.equipment, material: sum.material + item.material, fuel: sum.fuel + item.fuel, total: sum.total + item.total }), { personnel: 0, equipment: 0, material: 0, fuel: 0, total: 0 });
}

function costFactor(basis: string, days: number, quantity: number): number {
  if (basis === 'PER_HOUR') return days * 8 * quantity;
  if (basis === 'PER_WEEK') return days / 7 * quantity;
  if (basis === 'PER_MONTH') return days / 30 * quantity;
  if (basis === 'PER_UNIT') return quantity;
  if (basis === 'FIXED') return 1;
  return days * quantity;
}

function daysInclusive(start: string, end: string): number { return Math.round((new Date(`${end}T00:00:00`).getTime() - new Date(`${start}T00:00:00`).getTime()) / 86_400_000) + 1; }
