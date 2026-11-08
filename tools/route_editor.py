"""Offline desktop CSV route entry. Run with Python 3; uses only the standard library."""
import csv
import io
import math
import tkinter as tk
from tkinter import filedialog, messagebox


def validate(text):
    points = []
    for number, row in enumerate(csv.reader(io.StringIO(text)), 1):
        if not row or row[0].strip().startswith('#') or row == ['latitude', 'longitude']:
            continue
        try:
            if len(row) != 2:
                raise ValueError()
            lat, lon = map(float, row)
            if not (math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180):
                raise ValueError()
        except ValueError as exc:
            raise ValueError(f'第 {number} 行无效，请按 纬度,经度 输入') from exc
        points.append((lat, lon))
    if len(set(points)) < 2:
        raise ValueError('至少需要两个不同点')
    if len(points) > 10000:
        raise ValueError('最多 10000 点')
    return points


def main():
    root = tk.Tk()
    root.title('轨迹复演 · 电脑路线录入')
    root.geometry('650x500')
    tk.Label(root, text='使用 WGS84 坐标，每行：纬度,经度（单位：度）\n从高德或百度复制的坐标须先确认坐标系，不能当作 WGS84 使用。').pack(pady=10)
    text = tk.Text(root, undo=True)
    text.pack(fill='both', expand=True, padx=12)
    text.insert('1.0', 'latitude,longitude\n')

    def save():
        try:
            points = validate(text.get('1.0', 'end'))
            path = filedialog.asksaveasfilename(defaultextension='.csv', filetypes=[('路线 CSV', '*.csv')])
            if not path:
                return
            with open(path, 'w', encoding='utf-8', newline='') as stream:
                writer = csv.writer(stream)
                writer.writerow(['latitude', 'longitude'])
                writer.writerows(points)
            messagebox.showinfo('已保存', f'{len(points)} 个点。\n将 CSV 复制到手机，在应用中选择“导入电脑路线 CSV”。')
        except (ValueError, OSError) as exc:
            messagebox.showerror('无法保存', str(exc))

    tk.Button(root, text='检查并导出 CSV', command=save).pack(pady=10)
    root.mainloop()


if __name__ == '__main__':
    main()
