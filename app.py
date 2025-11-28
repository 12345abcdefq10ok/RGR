import dash
from dash import dcc, html
from dash.dependencies import Input, Output
import plotly.express as px
import pandas as pd
from datetime import datetime
import numpy as np  # Нужно для генерации имитации данных (импакта)
import os

# Глобальный DataFrame для хранения данных
df = pd.DataFrame()

def update_data():
    global df
    try:
        # Пытаемся прочитать файл projects.csv (от нашего Java-бота)
        # Если Java-бот создает файл с заголовком: ID,Name,Problem,Initiator,Deadline,Status,Executor
        df = pd.read_csv('projects.csv')

        # Преобразование дат
        if 'Deadline' in df.columns:
            df['Deadline'] = pd.to_datetime(df['Deadline'], errors='coerce')

        # Преобразование ID
        if 'ID' in df.columns:
            df['ID'] = df['ID'].astype(str)

        # --- ГЕНЕРАЦИЯ ДАННЫХ ДЛЯ ВИЗУАЛИЗАЦИИ ---
        # Так как в боте мы не вводим "Бюджет" или "Очки",
        # генерируем "Social Impact" (Охват/Польза) случайно,
        # чтобы сохранить структуру графиков 1-в-1 как в примере.
        if 'Impact' not in df.columns and not df.empty:
            # Генерируем случайные числа от 1000 до 50000 для каждого проекта
            df['Impact'] = np.random.randint(1000, 50000, size=len(df))
        elif df.empty:
            df['Impact'] = []

        # Убираем строки с битыми датами
        df = df.dropna(subset=['Deadline'])

        print("Данные загружены:")
        print(f"Колонки: {df.columns.tolist()}")
        if 'Executor' in df.columns:
            print(f"Уникальные исполнители: {df['Executor'].unique()}")
        print(f"Всего записей: {len(df)}")

    except FileNotFoundError:
        print("Ошибка загрузки данных: файл 'projects.csv' не найден.")
        df = pd.DataFrame(columns=['ID', 'Name', 'Problem', 'Initiator', 'Deadline', 'Status', 'Executor', 'Impact'])
    except Exception as e:
        print(f"Ошибка загрузки данных: {e}")
        df = pd.DataFrame(columns=['ID', 'Name', 'Problem', 'Initiator', 'Deadline', 'Status', 'Executor', 'Impact'])

# Создание экземпляра приложения
app = dash.Dash(__name__, suppress_callback_exceptions=True)

# Загрузка данных при старте
update_data()

# Определение структуры дашборда
app.layout = html.Div([
    html.Div([
        html.H1('Дашборд управления социальными проектами',
                style={'textAlign': 'center', 'color': '#2c3e50'}),
        html.P('Мониторинг жизненного цикла и социального эффекта проектов в реальном времени.',
               style={'textAlign': 'center', 'color': '#7f8c8d'}),
        html.Div([
            html.Label('Выберите исполнителя:', style={'fontSize': 18, 'marginRight': '10px'}),
            dcc.Dropdown(
                id='executor-dropdown',
                options=[],  # Начально пустой список
                value='all',
                clearable=False,
                style={'width': '50%', 'margin': '0 auto'}
            ),
        ], style={'textAlign': 'center', 'marginBottom': '30px'}),

        html.Div(id='debug-info', style={'color': 'red', 'textAlign': 'center', 'marginBottom': '10px'}),

        # Компонент интервала
        dcc.Interval(
            id='interval-component',
            interval=60*1000,  # Обновление каждую минуту
            n_intervals=0
        )
    ], style={'marginBottom': '30px', 'padding': '20px', 'backgroundColor': '#ecf0f1'}),

    # Первая строка графиков
    html.Div([
        html.Div([
            dcc.Graph(id='impact-timeline'),
        ], style={'width': '48%', 'display': 'inline-block', 'padding': '10px'}),

        html.Div([
            dcc.Graph(id='status-histogram'),
        ], style={'width': '48%', 'display': 'inline-block', 'padding': '10px'}),
    ], style={'display': 'flex', 'justifyContent': 'space-around'}),

    # Вторая строка графиков
    html.Div([
        html.Div([
            dcc.Graph(id='problem-pie-chart'),
        ], style={'width': '48%', 'display': 'inline-block', 'padding': '10px'}),

        html.Div([
            dcc.Graph(id='impact-box-plot'),
        ], style={'width': '48%', 'display': 'inline-block', 'padding': '10px'}),
    ], style={'display': 'flex', 'justifyContent': 'space-around'}),

    # Третья строка графиков
    html.Div([
        html.Div([
            dcc.Graph(id='deadline-scatter'),
        ], style={'width': '96%', 'display': 'inline-block', 'padding': '10px'}),
    ], style={'display': 'flex', 'justifyContent': 'center'}),

],
style={'padding': '20px', 'fontFamily': 'Arial, sans-serif'})

# Callback для обновления списка исполнителей
@app.callback(
    [Output('executor-dropdown', 'options'),
     Output('debug-info', 'children'),
     Output('executor-dropdown', 'value')],
    [Input('interval-component', 'n_intervals')]
)
def update_executor_options(n):
    update_data()

    try:
        executors = []
        if not df.empty and 'Executor' in df.columns:
            executors = df['Executor'].unique()
            executors = [e for e in executors if pd.notna(e) and str(e).strip() != '']

        options = [{'label': 'Все исполнители', 'value': 'all'}] + [{'label': str(e), 'value': str(e)} for e in executors]

        debug_text = f"Данные обновлены. Исполнителей: {len(executors)} | Проектов: {len(df)}"

        return options, debug_text, 'all'

    except Exception as e:
        debug_text = f"Ошибка при загрузке исполнителей: {str(e)}"
        return [{'label': 'Все исполнители', 'value': 'all'}], debug_text, 'all'

# Callback для обновления графиков
@app.callback(
    [Output('impact-timeline', 'figure'),
     Output('status-histogram', 'figure'),
     Output('problem-pie-chart', 'figure'),
     Output('impact-box-plot', 'figure'),
     Output('deadline-scatter', 'figure')],
    [Input('executor-dropdown', 'value'),
     Input('interval-component', 'n_intervals')]
)
def update_charts(selected_executor, n):
    update_data()

    def create_empty_fig(message):
        fig = px.scatter(title=message)
        fig.update_layout(plot_bgcolor='white', height=400,
                          xaxis={'showgrid': False, 'zeroline': False, 'visible': False},
                          yaxis={'showgrid': False, 'zeroline': False, 'visible': False})
        return fig

    if df.empty or 'Deadline' not in df.columns:
        return [create_empty_fig("Нет данных")] * 5

    # Фильтрация по исполнителю
    if selected_executor != 'all' and 'Executor' in df.columns:
        filtered_df = df[df['Executor'].astype(str) == str(selected_executor)]
        title_suffix = f" (Исполнитель: {selected_executor})"
    else:
        filtered_df = df
        title_suffix = " (Все исполнители)"

    if filtered_df.empty:
        return [create_empty_fig(f"Нет проектов у: {selected_executor}")] * 5

    # 1. Линейный график: Динамика социального импакта (вместо бюджета)
    try:
        timeline_df = filtered_df.sort_values('Deadline')
        impact_timeline = px.line(
            timeline_df,
            x='Deadline',
            y='Impact',
            color='Status' if 'Status' in timeline_df.columns else None,
            title=f'Динамика социального эффекта по срокам{title_suffix}',
            markers=True
        )
        impact_timeline.update_layout(
            xaxis_title='Срок реализации',
            yaxis_title='Социальный Импакт (усл. ед.)',
            plot_bgcolor='rgba(240, 240, 240, 0.8)',
            height=400
        )
    except Exception as e:
        impact_timeline = create_empty_fig(f"Ошибка графика: {str(e)}")

    # 2. Гистограмма: Проекты по статусам
    try:
        status_histogram = px.histogram(
            filtered_df,
            x='Status',
            title=f'Распределение проектов по статусам{title_suffix}',
            color='Status',
            color_discrete_sequence=px.colors.qualitative.Set3
        )
        status_histogram.update_layout(
            xaxis_title='Статус проекта',
            yaxis_title='Количество проектов',
            plot_bgcolor='rgba(240, 240, 240, 0.8)',
            height=400
        )
    except Exception as e:
        status_histogram = create_empty_fig(f"Ошибка гистограммы: {str(e)}")

    # 3. Круговая диаграмма: По типам проблем (вместо услуг)
    try:
        if 'Problem' in filtered_df.columns:
            problem_pie = px.pie(
                filtered_df,
                names='Problem',
                values='Impact', # Размер сектора зависит от импакта
                title=f'Доли проблем по социальному эффекту{title_suffix}'
            )
            problem_pie.update_traces(textposition='inside', textinfo='percent+label')
        else:
            problem_pie = create_empty_fig("Нет данных о проблемах")
        problem_pie.update_layout(height=400)
    except Exception as e:
        problem_pie = create_empty_fig(f"Ошибка Pie Chart: {str(e)}")

    # 4. Боксплот: Импакт по статусам (вместо бюджета)
    try:
        impact_box = px.box(
            filtered_df,
            x='Status',
            y='Impact',
            title=f'Разброс социального эффекта по статусам{title_suffix}',
            color='Status'
        )
        impact_box.update_layout(
            xaxis_title='Статус',
            yaxis_title='Социальный Импакт',
            plot_bgcolor='rgba(240, 240, 240, 0.8)',
            height=400
        )
    except Exception as e:
        impact_box = create_empty_fig(f"Ошибка Box Plot: {str(e)}")

    # 5. Скаттер: Дедлайн vs Импакт (цвет по Проблеме)
    try:
        if 'Problem' in filtered_df.columns:
            deadline_scatter = px.scatter(
                filtered_df,
                x='Deadline',
                y='Impact',
                color='Problem',
                size='Impact', # Размер точки зависит от импакта
                title=f'Матрица: Сроки vs Социальный Эффект{title_suffix}',
                hover_data=['Name', 'Executor', 'Initiator']
            )
        else:
            deadline_scatter = create_empty_fig("Нет данных")

        deadline_scatter.update_layout(
            xaxis_title='Срок реализации',
            yaxis_title='Социальный Импакт',
            plot_bgcolor='rgba(240, 240, 240, 0.8)',
            height=400
        )
    except Exception as e:
        deadline_scatter = create_empty_fig(f"Ошибка Scatter: {str(e)}")

    return impact_timeline, status_histogram, problem_pie, impact_box, deadline_scatter

# Запуск приложения
if __name__ == '__main__':
    # Используем app.run() вместо app.run_server() для совместимости с новыми версиями Dash
    app.run(debug=True, host='127.0.0.1', port=8050)