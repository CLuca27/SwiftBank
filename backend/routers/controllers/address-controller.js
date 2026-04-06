const GOOGLE_PLACES_BASE = 'https://maps.googleapis.com/maps/api/place';

async function autocomplete(req, res) {
    try {
        const { input } = req.query;

        if (!input) {
            return res.json({
                success: true,
                data: []
            });
        }

        const url = `${GOOGLE_PLACES_BASE}/autocomplete/json?` +
            `input=${encodeURIComponent(input)}` +
            `&types=address` +
            `&components=country:ro` +
            `&language=ro` +
            `&key=${process.env.GOOGLE_PLACES_API_KEY}`;

        const response = await fetch(url);
        const data = await response.json();

        if (data.status !== 'OK' && data.status !== 'ZERO_RESULTS') {
            console.error('Google API error:', data.status, data.error_message);
            return res.status(502).json({
                success: false,
                error: {
                    code: 'GOOGLE_API_ERROR',
                    message: 'Eroare la căutarea adresei'
                }
            });
        }

        const suggestions = (data.predictions || []).map(p => ({
            place_id: p.place_id,
            description: p.description
        }));

        console.log('Sugestii preluate cu succes!');
        suggestions.forEach(s => console.log(s.place_id, s.description));

        return res.json({
            success: true,
            data: suggestions
        });

    } catch (error) {
        console.error('Eroare autocomplete:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la căutarea adresei'
            }
        });
    }
}

async function getPlaceDetails(req, res) {
    try {
        const { place_id } = req.query;

        if (!place_id) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_REQUIRED_FIELDS',
                    message: 'place_id este obligatoriu'
                }
            });
        }

        const url = `${GOOGLE_PLACES_BASE}/details/json?` +
            `place_id=${encodeURIComponent(place_id)}` +
            `&fields=address_components,formatted_address` +
            `&language=ro` +
            `&key=${process.env.GOOGLE_PLACES_API_KEY}`;

        const response = await fetch(url);
        const data = await response.json();

        if (data.status !== 'OK') {
            console.error('Google API error:', data.status, data.error_message);
            return res.status(502).json({
                success: false,
                error: {
                    code: 'GOOGLE_API_ERROR',
                    message: 'Eroare la obținerea detaliilor adresei'
                }
            });
        }

        const components = data.result.address_components || [];

        const getComponent = (type) => {
            const comp = components.find(c => c.types.includes(type));
            return comp?.long_name || null;
        };

        const address = {
            formatted_address: data.result.formatted_address,
            street_number: getComponent('street_number'),
            street: getComponent('route'),
            city: getComponent('locality') || getComponent('administrative_area_level_2'),
            county: getComponent('administrative_area_level_1'),
            postal_code: getComponent('postal_code'),
            country: getComponent('country')
        };

        return res.json({
            success: true,
            data: address
        });

    } catch (error) {
        console.error('Eroare getPlaceDetails:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea detaliilor adresei'
            }
        });
    }
}

export default {
    autocomplete,
    getPlaceDetails
}