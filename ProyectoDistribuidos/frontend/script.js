        let cryptoData = [];
        let lastUpdateTime = new Date();

        // Navegación entre secciones
        document.querySelectorAll('.nav-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                // Remover active de todos los tabs y secciones
                document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
                document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
                
                // Activar tab y sección correspondiente
                tab.classList.add('active');
                const sectionId = tab.getAttribute('data-section');
                document.getElementById(sectionId).classList.add('active');
            });
        });

        // Función para obtener datos de criptomonedas
        async function fetchCryptoPrices() {
            const cryptoContainer = document.getElementById('crypto-container');
            
            try {
                const response = await fetch('http://localhost:8080/precios'); //http://localhost:8080/precios
                
                if (!response.ok) {
                    throw new Error(`Error HTTP: ${response.status}`);
                }

                cryptoData = await response.json();
                lastUpdateTime = new Date();
                
                // Actualizar timestamp
                document.getElementById('lastUpdate').textContent = 
                    `Última actualización: ${lastUpdateTime.toLocaleTimeString()}`;

                // Limpiar contenedor y mostrar datos
                cryptoContainer.innerHTML = '';

                cryptoData.forEach(crypto => {
                    const card = document.createElement('div');
                    card.className = 'crypto-card';
                    
                    // Crear iniciales para el ícono
                    const initials = crypto.symbol.substring(0, 2);
                    
                    card.innerHTML = `
                        <div class="crypto-header">
                            <div class="crypto-icon">${initials}</div>
                            <div class="crypto-info">
                                <h3>${crypto.name}</h3>
                                <div class="crypto-symbol">${crypto.symbol}</div>
                            </div>
                        </div>
                        <div class="crypto-price">$${crypto.price.toFixed(2)}</div>
                    `;
                    
                    cryptoContainer.appendChild(card);
                });

                // Actualizar selects con las criptomonedas disponibles
                updateCryptoSelects();

            } catch (error) {
                cryptoContainer.innerHTML = `
                    <div style="grid-column: 1 / -1; text-align: center; color: var(--red-color);">
                        <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                        <h3>Error al cargar datos</h3>
                        <p>Verifica que el servidor Java esté corriendo en puerto 8080</p>
                        <p style="font-size: 0.9rem; opacity: 0.8;">Error: ${error.message}</p>
                    </div>
                `;
                console.error('Error al obtener precios:', error);
            }
        }

        // Actualizar selects con criptomonedas disponibles
        function updateCryptoSelects() {
            const selects = [
                'single-crypto-select',
                'regression-crypto',
                'compare-cryptos'
            ];

            selects.forEach(selectId => {
                const select = document.getElementById(selectId);
                if (select) {
                    // Mantener primer option si existe
                    const firstOption = select.querySelector('option[value=""]');
                    select.innerHTML = '';
                    
                    if (firstOption && selectId !== 'compare-cryptos') {
                        select.appendChild(firstOption);
                    }
                    
                    cryptoData.forEach(crypto => {
                        const option = document.createElement('option');
                        option.value = crypto.symbol;
                        option.textContent = `${crypto.name} (${crypto.symbol})`;
                        select.appendChild(option);
                    });
                }
            });
        }

        // Funciones para generar gráficos (simuladas por ahora)
        function generateSingleChart() {
            const crypto = document.getElementById('single-crypto-select').value;
            const hours = document.getElementById('single-hours').value;
            const container = document.getElementById('single-chart-result');
            
            if (!crypto) {
                alert('Por favor selecciona una criptomoneda');
                return;
            }
            
            container.innerHTML = `
                <div style="text-align: center;">
                    <div class="loading">
                        <div class="spinner"></div>
                        Generando gráfico de ${crypto} para las últimas ${hours} horas...
                    </div>
                </div>
            `;
            
            // Simular carga
            setTimeout(() => {
                container.innerHTML = `
                    <div style="text-align: center;">
                        <i class="fas fa-chart-line" style="font-size: 3rem; color: var(--green-color); margin-bottom: 1rem;"></i>
                        <h3>Gráfico de ${crypto}</h3>
                        <p>Variación de precio en las últimas ${hours} horas</p>
                        <p style="margin-top: 1rem; opacity: 0.7;">
                            [Aquí se mostraría el gráfico generado por el microservicio de backend]
                        </p>
                    </div>
                `;
            }, 2000);
        }

        function generateAllCharts() {
            const hours = document.getElementById('all-hours').value;
            const container = document.getElementById('all-charts-result');
            
            container.innerHTML = `
                <div style="text-align: center;">
                    <div class="loading">
                        <div class="spinner"></div>
                        Generando gráficos de todas las criptomonedas para las últimas ${hours} horas...
                    </div>
                </div>
            `;
            
            setTimeout(() => {
                container.innerHTML = `
                    <div style="text-align: center;">
                        <i class="fas fa-chart-area" style="font-size: 3rem; color: var(--green-color); margin-bottom: 1rem;"></i>
                        <h3>Gráficos de Todas las Criptomonedas</h3>
                        <p>Variación de precio de las 10 criptomonedas en las últimas ${hours} horas</p>
                        <p style="margin-top: 1rem; opacity: 0.7;">
                            [Aquí se mostrarían los 10 gráficos individuales generados por el backend]
                        </p>
                    </div>
                `;
            }, 3000);
        }

        function generateCompareChart() {
            const cryptos = Array.from(document.getElementById('compare-cryptos').selectedOptions)
                               .map(option => option.value);
            const startHour = document.getElementById('compare-start').value;
            const endHour = document.getElementById('compare-end').value;
            const container = document.getElementById('compare-chart-result');
            
            if (cryptos.length === 0) {
                alert('Por favor selecciona al menos una criptomoneda');
                return;
            }
            
            if (parseInt(endHour) <= parseInt(startHour)) {
                alert('La hora de fin debe ser mayor que la hora de inicio');
                return;
            }
            
            container.innerHTML = `
                <div style="text-align: center;">
                    <div class="loading">
                        <div class="spinner"></div>
                        Generando comparación de ${cryptos.join(', ')} desde las ${startHour}:00 hasta las ${endHour}:00...
                    </div>
                </div>
            `;
            
            setTimeout(() => {
                container.innerHTML = `
                    <div style="text-align: center;">
                        <i class="fas fa-balance-scale" style="font-size: 3rem; color: var(--green-color); margin-bottom: 1rem;"></i>
                        <h3>Comparación de Criptomonedas</h3>
                        <p>Gráfico superpuesto de: ${cryptos.join(', ')}</p>
                        <p>Período: ${startHour}:00 - ${endHour}:00</p>
                        <p style="margin-top: 1rem; opacity: 0.7;">
                            [Aquí se mostraría el gráfico superpuesto con diferentes colores para cada crypto]
                        </p>
                    </div>
                `;
            }, 2500);
        }

        function generateRegressionChart() {
            const crypto = document.getElementById('regression-crypto').value;
            const startHour = document.getElementById('regression-start').value;
            const endHour = document.getElementById('regression-end').value;
            const container = document.getElementById('regression-chart-result');
            
            if (!crypto) {
                alert('Por favor selecciona una criptomoneda');
                return;
            }
            
            if (parseInt(endHour) <= parseInt(startHour)) {
                alert('La hora de fin debe ser mayor que la hora de inicio');
                return;
            }
            
            container.innerHTML = `
                <div style="text-align: center;">
                    <div class="loading">
                        <div class="spinner"></div>
                        Calculando regresión lineal de ${crypto} desde las ${startHour}:00 hasta las ${endHour}:00...
                    </div>
                </div>
            `;
            
            setTimeout(() => {
                // Generar ecuación simulada
                const slope = (Math.random() * 100 - 50).toFixed(2);
                const intercept = (Math.random() * 50000).toFixed(2);
                
                container.innerHTML = `
                    <div style="text-align: center;">
                        <i class="fas fa-calculator" style="font-size: 3rem; color: var(--green-color); margin-bottom: 1rem;"></i>
                        <h3>Regresión Lineal - ${crypto}</h3>
                        <p>Período: ${startHour}:00 - ${endHour}:00</p>
                        <div style="background: #f8f9fa; padding: 1rem; border-radius: 8px; margin: 1rem 0; color: var(--text-dark);">
                            <strong>Ecuación de la recta: y = ${slope}x + ${intercept}</strong>
                        </div>
                        <p style="opacity: 0.7;">
                            [Aquí se mostraría el gráfico de puntos con la línea de regresión]
                        </p>
                    </div>
                `;
            }, 3000);
        }

        // Inicialización
        document.addEventListener('DOMContentLoaded', () => {
            fetchCryptoPrices();
            // Actualizar cada minuto
            setInterval(fetchCryptoPrices, 60000);
        });