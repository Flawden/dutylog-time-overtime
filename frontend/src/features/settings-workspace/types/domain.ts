export interface DutyLogSettingsWorkspaceDomain {
  ready(): boolean;
  openShiftTypeManager(editId?: number | null): void;
  closeShiftTypeManager(): void;
  refreshShiftTypes(): Promise<void>;
  snapshot(): Readonly<{ shiftTypes: number; editingId: number | null; managerOpen: boolean }>;
}
