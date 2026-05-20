import { Observable } from 'rxjs';

export interface CrudListController<T> {
  items: T[];
  loading: boolean;
  error: string;
  setItems(items: T[]): void;
}

export function createCrudListController<T>(): CrudListController<T> {
  return {
    items: [],
    loading: false,
    error: '',
    setItems(items: T[]) {
      this.items = items;
    }
  };
}

export function deleteAndReload<T>(
  delete$: Observable<unknown>,
  reload$: Observable<T[]>,
  assign: (items: T[]) => void
): void {
  delete$.subscribe(() => reload$.subscribe(assign));
}
